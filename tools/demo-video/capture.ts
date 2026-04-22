import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { type BrowserContext, chromium, devices, type Page } from "@playwright/test";
import {
  DPR,
  FPS,
  MOBILE_VIEWPORT,
  SHOTS,
  type Shot,
  type ShotManifest,
  type ShotManifestEntry,
  VIEWPORT,
} from "./shots.js";

const BASE_URL = process.env.DEMO_BASE_URL ?? "https://asoview-clone-dev.duckdns.org";
const FIREBASE_API_KEY = mustEnv("E2E_FIREBASE_API_KEY");
const TEST_EMAIL = mustEnv("E2E_TEST_EMAIL");
const TEST_PASSWORD = mustEnv("E2E_TEST_PASSWORD");
const OUT_DIR = join(import.meta.dirname, "out");
const SHOTS_DIR = join(OUT_DIR, "screenshots");

function mustEnv(name: string): string {
  const v = process.env[name];
  if (!v) {
    throw new Error(`Missing env var: ${name}`);
  }
  return v;
}

async function pickProduct(
  idToken: string,
): Promise<{ id: string; venueId: string }> {
  const listRes = await fetch(`${BASE_URL}/api/v1/products?size=20`);
  if (!listRes.ok) throw new Error(`Failed to list products: ${listRes.status}`);
  const json = (await listRes.json()) as {
    content?: Array<{ id?: string; venueId?: string }>;
  };
  const candidates = (json.content ?? []).filter(
    (p): p is { id: string; venueId: string } => !!p.id && !!p.venueId,
  );
  if (candidates.length === 0) {
    throw new Error("No products with venueId available for screenshots");
  }
  // Probe ahead 14 days via the product-availability endpoint (the
  // reservation-slots one is unpopulated on dev). The empty-form
  // "failed to fetch slots" shot is the worst possible demo frame, so fail
  // hard here if no slots are visible anywhere.
  for (const product of candidates) {
    const today = new Date();
    const to = new Date();
    to.setUTCDate(today.getUTCDate() + 14);
    const res = await fetch(
      `${BASE_URL}/api/v1/products/${encodeURIComponent(product.id)}/availability?from=${today
        .toISOString()
        .slice(0, 10)}&to=${to.toISOString().slice(0, 10)}`,
      { headers: { Authorization: `Bearer ${idToken}` } },
    );
    if (!res.ok) continue;
    const entries = (await res.json()) as Array<{ slots?: Array<{ remainingCapacity?: number }> }>;
    const hasSlot = entries.some((e) => (e.slots ?? []).some((s) => (s.remainingCapacity ?? 0) > 0));
    if (hasSlot) {
      console.log(`  picked ${product.id} (venue ${product.venueId})`);
      return product;
    }
  }
  console.warn("No product with slots in the next 14 days — falling back to first product");
  return candidates[0];
}

async function signInViaUI(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/ja/signin`);
  await page.getByTestId("email-input").fill(TEST_EMAIL);
  await page.getByTestId("password-input").fill(TEST_PASSWORD);
  await page.getByRole("button", { name: /sign in with email/i }).click();
  await page.waitForURL((u) => !u.toString().includes("signin"), { timeout: 20_000 });
}

async function getIdToken(): Promise<string> {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: TEST_EMAIL,
        password: TEST_PASSWORD,
        returnSecureToken: true,
      }),
    },
  );
  if (!res.ok) throw new Error(`Firebase auth failed: ${res.status} ${await res.text()}`);
  const data = (await res.json()) as { idToken?: string };
  if (!data.idToken) throw new Error("No idToken in Firebase response");
  return data.idToken;
}

async function seedFavorite(idToken: string, productId: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/api/v1/me/favorites/${productId}`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${idToken}` },
  });
  if (!res.ok && res.status !== 204) {
    console.warn(`Favorite seed returned ${res.status} — continuing`);
  }
}

async function provisionUser(idToken: string): Promise<void> {
  await fetch(`${BASE_URL}/api/v1/me`, {
    headers: { Authorization: `Bearer ${idToken}` },
  });
}

async function captureShot(
  page: Page,
  shot: Shot,
  resolvedRoute: string,
  viewport: { width: number; height: number },
): Promise<ShotManifestEntry> {
  if (shot.kind !== "capture") {
    throw new Error(`captureShot called on non-capture shot ${shot.id}`);
  }
  const url = `${BASE_URL}${resolvedRoute}`;
  console.log(`  → ${shot.id} :: ${url}`);
  await page.goto(url, { waitUntil: "domcontentloaded" });
  if (shot.waitFor?.selector) {
    await page
      .locator(shot.waitFor.selector)
      .first()
      .waitFor({ state: "visible", timeout: shot.waitFor.timeoutMs ?? 15_000 });
  }
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(600);
  if (shot.preCapture) await shot.preCapture(page);
  await page.waitForTimeout(200);

  const filename = `${shot.id}.png`;
  await page.screenshot({
    path: join(SHOTS_DIR, filename),
    fullPage: false,
    animations: "disabled",
    clip: { x: 0, y: 0, width: viewport.width, height: viewport.height },
  });

  const annotations: ShotManifestEntry["annotations"] = [];
  for (const a of shot.annotations ?? []) {
    const locator = page.locator(a.selector).first();
    try {
      await locator.waitFor({ state: "visible", timeout: 3_000 });
      const box = await locator.boundingBox();
      if (!box) continue;
      const clamped = clampBox(box, viewport);
      if (!clamped) continue;
      annotations.push({
        label: a.label,
        pointFrom: a.pointFrom,
        tone: a.tone,
        ...clamped,
      });
    } catch {
      console.warn(`    annotation skipped: ${a.selector} not visible`);
    }
  }

  return {
    id: shot.id,
    image: filename,
    durationSec: shot.durationSec,
    caption: shot.caption,
    annotations,
  };
}

function clampBox(
  box: { x: number; y: number; width: number; height: number },
  viewport: { width: number; height: number },
): { x: number; y: number; width: number; height: number } | null {
  const x = Math.max(0, Math.round(box.x));
  const y = Math.max(0, Math.round(box.y));
  const right = Math.min(viewport.width, Math.round(box.x + box.width));
  const bottom = Math.min(viewport.height, Math.round(box.y + box.height));
  const width = right - x;
  const height = bottom - y;
  if (width <= 0 || height <= 0) return null;
  return { x, y, width, height };
}

async function newDesktopContext(browser: Awaited<ReturnType<typeof chromium.launch>>) {
  return browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: DPR,
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
  });
}

async function newMobileContext(browser: Awaited<ReturnType<typeof chromium.launch>>) {
  return browser.newContext({
    ...devices["iPhone 14"],
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
  });
}

async function main() {
  await mkdir(SHOTS_DIR, { recursive: true });

  console.log(`Base URL: ${BASE_URL}`);
  console.log("Signing in + seeding data…");
  const idToken = await getIdToken();
  await provisionUser(idToken);
  const product = await pickProduct(idToken);
  await seedFavorite(idToken, product.id);
  console.log(`Seeded favorite for product ${product.id} (venue ${product.venueId})`);

  const browser = await chromium.launch();
  const authContext = await newDesktopContext(browser);
  const anonContext = await newDesktopContext(browser);
  const mobileContext = await newMobileContext(browser);
  // Prime the mobile context so <InstallPrompt> considers this a second
  // visit — the banner only renders when visit-count >= 2. Best-effort:
  // the shot does not depend on the banner firing.
  await mobileContext.addInitScript(() => {
    try {
      localStorage.setItem("pwa:visit-count", "2");
    } catch {
      // Storage access may be blocked pre-navigation; ignore.
    }
  });

  try {
    const authPage = await authContext.newPage();
    await signInViaUI(authPage);
    const anonPage = await anonContext.newPage();
    const mobilePage = await mobileContext.newPage();
    await signInViaUI(mobilePage);

    const pagesByContext: Record<"auth" | "anon" | "mobile", Page> = {
      auth: authPage,
      anon: anonPage,
      mobile: mobilePage,
    };
    const viewportByContext: Record<"auth" | "anon" | "mobile", { width: number; height: number }> = {
      auth: VIEWPORT,
      anon: VIEWPORT,
      mobile: MOBILE_VIEWPORT,
    };

    const manifest: ShotManifest = { viewport: VIEWPORT, dpr: DPR, fps: FPS, shots: [] };
    for (const shot of SHOTS) {
      if (shot.kind === "prerendered") {
        // Pass-through: the Remotion renderer will mount the named
        // component in place of an <Img>. Hand-authored annotations are
        // already in the shot definition; forward them as-is.
        console.log(`  → ${shot.id} :: [prerendered ${shot.component}]`);
        manifest.shots.push({
          id: shot.id,
          image: "",
          component: shot.component,
          durationSec: shot.durationSec,
          caption: shot.caption,
          annotations: shot.annotations ?? [],
        });
        continue;
      }

      const ctx = shot.context ?? (shot.requiresAuth === false ? "anon" : "auth");
      const page = pagesByContext[ctx];
      const route = shot.route.replace("__PRODUCT_DETAIL__", `/ja/products/${product.id}`);
      const entry = await captureShot(page, shot, route, viewportByContext[ctx]);
      manifest.shots.push(entry);
    }

    await writeFile(join(OUT_DIR, "shots.json"), JSON.stringify(manifest, null, 2));

    // base64-inlined manifest: Remotion bundler imports this directly.
    const inlined = {
      ...manifest,
      shots: await Promise.all(
        manifest.shots.map(async (s) => {
          if (!s.image) return s; // prerendered shot, no image file
          const bytes = await readFile(join(SHOTS_DIR, s.image));
          return { ...s, image: `data:image/png;base64,${bytes.toString("base64")}` };
        }),
      ),
    };
    await writeFile(join(OUT_DIR, "shots.inline.json"), JSON.stringify(inlined));
    console.log(`Wrote ${manifest.shots.length} shots to ${OUT_DIR}`);
  } finally {
    await authContext.close();
    await anonContext.close();
    await mobileContext.close();
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
