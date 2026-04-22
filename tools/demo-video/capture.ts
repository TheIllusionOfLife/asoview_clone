import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { chromium, type BrowserContext, type Page } from "@playwright/test";
import {
  DPR,
  FPS,
  SHOTS,
  VIEWPORT,
  type Shot,
  type ShotManifest,
  type ShotManifestEntry,
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
  // Probe ahead 14 days for any venue that returns at least one slot. Slot
  // seeding in the dev catalog is sparse, and an empty reservation form
  // ("failed to fetch slots") is the worst possible demo frame.
  const tomorrow = new Date();
  tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  for (const product of candidates) {
    for (let day = 0; day < 14; day++) {
      const d = new Date(tomorrow);
      d.setUTCDate(tomorrow.getUTCDate() + day);
      const date = d.toISOString().slice(0, 10);
      const slotRes = await fetch(
        `${BASE_URL}/api/v1/reservation-slots?venueId=${product.venueId}&date=${date}`,
        { headers: { Authorization: `Bearer ${idToken}` } },
      );
      if (!slotRes.ok) continue;
      const slots = (await slotRes.json()) as Array<{ remainingCapacity?: number }>;
      if (Array.isArray(slots) && slots.some((s) => (s.remainingCapacity ?? 0) > 0)) {
        console.log(`  picked ${product.id} (venue ${product.venueId}, slots on ${date})`);
        return product;
      }
    }
  }
  console.warn("No venue with slots in the next 14 days — falling back to first product");
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
): Promise<ShotManifestEntry> {
  const url = `${BASE_URL}${resolvedRoute}`;
  console.log(`  → ${shot.id} :: ${url}`);
  await page.goto(url, { waitUntil: "domcontentloaded" });
  if (shot.waitFor?.selector) {
    await page
      .locator(shot.waitFor.selector)
      .first()
      .waitFor({ state: "visible", timeout: shot.waitFor.timeoutMs ?? 15_000 });
  }
  // Settle: wait for fonts + any in-view image decode. Images above the fold are
  // what readers notice in a static frame; scroll back to top so annotations
  // anchor to the composition user saw on initial paint.
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
    clip: { x: 0, y: 0, width: VIEWPORT.width, height: VIEWPORT.height },
  });

  const annotations: ShotManifestEntry["annotations"] = [];
  for (const a of shot.annotations ?? []) {
    const locator = page.locator(a.selector).first();
    try {
      await locator.waitFor({ state: "visible", timeout: 3_000 });
      const box = await locator.boundingBox();
      if (!box) continue;
      const clamped = clampBox(box);
      if (!clamped) continue;
      annotations.push({ ...a, ...clamped });
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
): { x: number; y: number; width: number; height: number } | null {
  const x = Math.max(0, Math.round(box.x));
  const y = Math.max(0, Math.round(box.y));
  const right = Math.min(VIEWPORT.width, Math.round(box.x + box.width));
  const bottom = Math.min(VIEWPORT.height, Math.round(box.y + box.height));
  const width = right - x;
  const height = bottom - y;
  if (width <= 0 || height <= 0) return null;
  return { x, y, width, height };
}

async function newContext(): Promise<BrowserContext> {
  const browser = await chromium.launch();
  return browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: DPR,
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

  const authContext = await newContext();
  const anonContext = await newContext();
  try {
    const authPage = await authContext.newPage();
    await signInViaUI(authPage);
    const anonPage = await anonContext.newPage();

    const manifest: ShotManifest = { viewport: VIEWPORT, dpr: DPR, fps: FPS, shots: [] };
    for (const shot of SHOTS) {
      const page = shot.requiresAuth === false ? anonPage : authPage;
      const route = shot.route
        .replace("__PRODUCT_DETAIL__", `/ja/products/${product.id}`)
        .replace("__RESERVE__", `/ja/reserve?venueId=${product.venueId}`);
      const entry = await captureShot(page, shot, route);
      manifest.shots.push(entry);
    }

    await writeFile(join(OUT_DIR, "shots.json"), JSON.stringify(manifest, null, 2));

    // Also write a base64-inlined manifest that Remotion's bundler can import
    // directly, sidestepping staticFile() + public-dir resolution quirks.
    const inlined = {
      ...manifest,
      shots: await Promise.all(
        manifest.shots.map(async (s) => {
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
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
