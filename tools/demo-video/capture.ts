import { createHash } from "node:crypto";
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

type ProductSummary = {
  id: string;
  venueId: string;
  title: string;
  areaName?: string;
  variants: Array<{ id: string; priceAmount: string }>;
};

type SlotPick = {
  product: ProductSummary;
  variantId: string;
  slotId: string;
  slotStartAt: string;
  slotEndAt: string;
  unitPrice: string;
};

/** Fetch up to N products with at least one slot in the next 14 days. */
async function pickSlotBackedProducts(
  idToken: string,
  count: number,
): Promise<SlotPick[]> {
  const listRes = await fetch(`${BASE_URL}/api/v1/products?size=50`);
  if (!listRes.ok) throw new Error(`Failed to list products: ${listRes.status}`);
  const json = (await listRes.json()) as {
    content?: Array<{
      id?: string;
      venueId?: string;
      title?: string;
      areaName?: string;
      variants?: Array<{ id?: string; priceAmount?: string | number }>;
    }>;
  };
  const candidates: ProductSummary[] = (json.content ?? []).flatMap((p) => {
    if (!p.id || !p.venueId) return [];
    const variants = (p.variants ?? []).flatMap((v) =>
      v.id && v.priceAmount != null
        ? [{ id: v.id, priceAmount: String(v.priceAmount) }]
        : [],
    );
    if (variants.length === 0) return [];
    return [{ id: p.id, venueId: p.venueId, title: p.title ?? p.id, areaName: p.areaName, variants }];
  });
  if (candidates.length < count) {
    throw new Error(`Need ${count} products with variants; found ${candidates.length}`);
  }
  const today = new Date();
  const to = new Date();
  to.setUTCDate(today.getUTCDate() + 14);
  const from = today.toISOString().slice(0, 10);
  const toIso = to.toISOString().slice(0, 10);
  const picks: SlotPick[] = [];
  for (const product of candidates) {
    if (picks.length >= count) break;
    const res = await fetch(
      `${BASE_URL}/api/v1/products/${encodeURIComponent(product.id)}/availability?from=${from}&to=${toIso}`,
      { headers: { Authorization: `Bearer ${idToken}` } },
    );
    if (!res.ok) continue;
    // API returns a flat array of { slotId, productVariantId, date,
    // startTime, endTime, remaining } — NOT a {slots: [...]} grouping.
    // Guard against a response shape drift (error object, HTML, etc.) so
    // the downstream .find() call never throws on .find of non-array.
    const raw = (await res.json()) as unknown;
    if (!Array.isArray(raw)) continue;
    const entries = raw as Array<{
      slotId?: string;
      productVariantId?: string;
      date?: string;
      startTime?: string;
      endTime?: string;
      remaining?: number;
    }>;
    // remaining >= 2 so the same slot could be reserved twice without a
    // capacity race. picks[0] gets used by the order, picks[1] by the
    // reservation — different products, so the flag is belt-and-suspenders.
    const slot = entries.find(
      (s) => (s.remaining ?? 0) >= 2 && s.slotId && s.date && s.startTime && s.endTime,
    );
    if (!slot || !slot.slotId || !slot.date || !slot.startTime || !slot.endTime) continue;
    const variantId = slot.productVariantId ?? product.variants[0].id;
    const variant = product.variants.find((v) => v.id === variantId) ?? product.variants[0];
    // Cart line expects ISO-like datetimes; splice date + hh:mm into the shape
    // apps/asoview-web/src/lib/cart.ts reads.
    picks.push({
      product,
      variantId: variant.id,
      slotId: slot.slotId,
      slotStartAt: `${slot.date}T${slot.startTime}:00`,
      slotEndAt: `${slot.date}T${slot.endTime}:00`,
      unitPrice: variant.priceAmount,
    });
  }
  if (picks.length < count) {
    throw new Error(`Only ${picks.length}/${count} products had a slot with capacity ≥ 2`);
  }
  for (const pick of picks) {
    console.log(
      `  picked ${pick.product.id} (slot ${pick.slotId} @ ${pick.slotStartAt})`,
    );
  }
  return picks;
}

async function signInViaUI(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/ja/signin`);
  await page.getByTestId("email-input").fill(TEST_EMAIL);
  await page.getByTestId("password-input").fill(TEST_PASSWORD);
  await page.getByRole("button", { name: /sign in with email/i }).click();
  await page.waitForURL((u) => !u.toString().includes("signin"), { timeout: 20_000 });
}

async function signInFirebase(): Promise<{ idToken: string; localId: string }> {
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
  const data = (await res.json()) as { idToken?: string; localId?: string };
  if (!data.idToken || !data.localId) throw new Error("No idToken/localId in Firebase response");
  return { idToken: data.idToken, localId: data.localId };
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

function uuid(): string {
  return crypto.randomUUID();
}

/** Deterministic UUID-shaped key derived from the input. Backend validates
 *  Idempotency-Key as a UUID, so a plain "demo-video:uid:..." string is
 *  rejected — we hash then format as a v5-like UUID. */
function deterministicUuid(key: string): string {
  const h = createHash("sha256").update(key).digest("hex").slice(0, 32);
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

/** POST /v1/orders with one line. Returns PENDING — no Stripe drive.
 *  Idempotency key is derived from {uid, date, productVariantId, slotId}.
 *  Date is UTC day-resolution so multiple captures within one day dedupe
 *  on the server, but a fresh day produces a fresh orderId. Fresh orderId
 *  matters because the dev mark-paid endpoint races against PaymentService's
 *  partial-unique-index on (order_id, status IN ('CREATED','PROCESSING'));
 *  a stale PROCESSING payment from a prior day would otherwise block
 *  today's confirm flow with a 409. */
async function seedOrder(idToken: string, uid: string, pick: SlotPick): Promise<string> {
  const today = new Date().toISOString().slice(0, 10);
  const idempotencyKey = deterministicUuid(
    `demo-video:${uid}:${today}:${pick.variantId}:${pick.slotId}`,
  );
  const res = await fetch(`${BASE_URL}/api/v1/orders`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({
      idempotencyKey,
      items: [
        { productVariantId: pick.variantId, slotId: pick.slotId, quantity: 1 },
      ],
    }),
  });
  if (!res.ok) {
    throw new Error(`Order seed failed: ${res.status} ${await res.text()}`);
  }
  const body = (await res.json()) as { orderId?: string; id?: string };
  const id = body.orderId ?? body.id;
  if (!id) throw new Error(`Order seed: unexpected response ${JSON.stringify(body)}`);
  return id;
}

/** POST /v1/reservations against a slot from reservation-service's dev
 *  seeder. Returns the reservationId or null on failure (we warn rather
 *  than throw so the capture run still produces a video if the seeder
 *  hasn't populated the target venue yet). */
async function seedReservation(
  idToken: string,
  uid: string,
  venueId: string,
): Promise<string | null> {
  const today = new Date().toISOString().slice(0, 10);
  const listRes = await fetch(
    `${BASE_URL}/api/v1/reservation-slots?venueId=${encodeURIComponent(venueId)}&date=${today}`,
    { headers: { Authorization: `Bearer ${idToken}` } },
  );
  if (!listRes.ok) {
    console.warn(`  reservation slots: ${listRes.status} — skipping seed`);
    return null;
  }
  const raw = (await listRes.json()) as unknown;
  if (!Array.isArray(raw)) {
    console.warn(`  reservation slots: unexpected response shape — skipping seed`);
    return null;
  }
  const slots = raw as Array<{
    slotId?: string;
    remainingCapacity?: number;
  }>;
  // remaining >= 1 is enough: a single demo reservation doesn't race any
  // other capture traffic. The seeder provisions capacity 8 per slot.
  const slot = slots.find((s) => s.slotId && (s.remainingCapacity ?? 0) >= 1);
  if (!slot?.slotId) {
    console.warn(`  reservation slots: no open slot on ${today} for venue ${venueId}`);
    return null;
  }
  const idempotencyKey = deterministicUuid(
    `demo-video:reservation:${uid}:${slot.slotId}`,
  );
  const res = await fetch(`${BASE_URL}/api/v1/reservations`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({
      slotId: slot.slotId,
      idempotencyKey,
      guestName: "デモ太郎",
      guestEmail: TEST_EMAIL,
      guestCount: 2,
    }),
  });
  if (!res.ok) {
    console.warn(`  reservation seed: ${res.status} ${await res.text()}`);
    return null;
  }
  const body = (await res.json()) as { reservationId?: string };
  return body.reservationId ?? null;
}

/** POST /v1/dev/orders/{orderId}/mark-paid (dev-only, see
 *  DevPaymentConfirmController). Drives the order through the production
 *  confirm path so PointEarnListener credits the user's ledger. */
async function confirmOrderForDemo(idToken: string, orderId: string): Promise<void> {
  const res = await fetch(
    `${BASE_URL}/api/v1/dev/orders/${encodeURIComponent(orderId)}/mark-paid`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${idToken}` },
    },
  );
  if (!res.ok) {
    console.warn(`  confirm order: ${res.status} ${await res.text()}`);
    return;
  }
  const body = (await res.json()) as { orderStatus?: string; detail?: string };
  console.log(`  order ${orderId} → ${body.orderStatus ?? "?"} (${body.detail ?? "?"})`);
}

/** Best-effort post-seed GET to confirm the resource is listed. Warns on
 *  miss rather than throwing so a transient listing delay doesn't abort
 *  the capture run. */
async function assertListed(
  idToken: string,
  path: string,
  matcher: (body: unknown) => boolean,
  label: string,
): Promise<void> {
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    if (!res.ok) {
      console.warn(`  ${label}: GET ${path} returned ${res.status}`);
      return;
    }
    const body = (await res.json()) as unknown;
    if (!matcher(body)) {
      console.warn(`  ${label}: seeded record not visible in ${path}`);
    }
  } catch (e) {
    console.warn(`  ${label}: post-seed check threw`, e);
  }
}

/** Cart is localStorage-backed. Injected into the authenticated context
 *  before signInViaUI so the /ja/cart shot has lines on first render. */
function buildCartLine(pick: SlotPick): Record<string, unknown> {
  return {
    productId: pick.product.id,
    productVariantId: pick.variantId,
    slotId: pick.slotId,
    slotStartAt: pick.slotStartAt,
    slotEndAt: pick.slotEndAt,
    quantity: 1,
    unitPrice: pick.unitPrice,
    productSnapshot: { name: pick.product.title, area: pick.product.areaName ?? null },
  };
}

/** Probe for the global error.tsx boundary ("問題が発生しました") after
 *  waitFor resolves. Without this, a shot whose waitFor selector
 *  accidentally matches an element rendered by BOTH the success path and
 *  error.tsx (e.g. "h1") will silently capture the error frame. Returns
 *  true if the boundary is currently visible.
 */
async function hasErrorBoundary(page: Page): Promise<boolean> {
  try {
    await page
      .locator("text=問題が発生しました")
      .first()
      .waitFor({ state: "visible", timeout: 500 });
    return true;
  } catch {
    return false;
  }
}

/** Navigate + wait for the shot's selector + probe for error.tsx.
 *  Throws if the selector times out OR the error boundary is showing.
 */
async function navigateAndVerify(page: Page, shot: Shot, url: string): Promise<void> {
  await page.goto(url, { waitUntil: "domcontentloaded" });
  if (shot.kind === "capture" && shot.waitFor?.selector) {
    await page
      .locator(shot.waitFor.selector)
      .first()
      .waitFor({ state: "visible", timeout: shot.waitFor.timeoutMs ?? 15_000 });
  }
  if (await hasErrorBoundary(page)) {
    throw new Error(`[${shot.id}] error boundary visible at ${url}`);
  }
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

  // Shot 03 has historically caught mid-rollout 5xxs from /v1/areas or
  // /v1/products, which makes Next render error.tsx. Log those responses
  // so a deterministic failure surfaces the offending body, and retry
  // once to absorb transient cluster flakes. Listener is shot-scoped and
  // detached before the shot returns.
  const shouldInstrument = shot.id === "03-area-landing";
  const captured: Array<{ url: string; status: number; body: string }> = [];
  const responseListener = shouldInstrument
    ? async (resp: import("@playwright/test").Response) => {
        const u = resp.url();
        if (!/\/v1\/(areas|products)(\?|$|\/)/.test(u)) return;
        let body = "";
        try {
          body = (await resp.text()).slice(0, 200);
        } catch {
          // body not readable (redirect, aborted) — skip
        }
        captured.push({ url: u, status: resp.status(), body });
      }
    : null;
  if (responseListener) page.on("response", responseListener);

  try {
    try {
      await navigateAndVerify(page, shot, url);
    } catch (err) {
      if (!shouldInstrument) throw err;
      console.warn(`    [${shot.id}] first attempt failed: ${(err as Error).message}`);
      for (const c of captured) {
        console.warn(`      ← ${c.status} ${c.url}  ${c.body}`);
      }
      captured.length = 0;
      await navigateAndVerify(page, shot, url);
    }
  } finally {
    if (responseListener) page.off("response", responseListener);
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

function hasContent(
  body: unknown,
  pred: (row: unknown) => boolean,
): boolean {
  if (Array.isArray(body)) return body.some(pred);
  if (body && typeof body === "object") {
    const arr = (body as { content?: unknown[] }).content;
    if (Array.isArray(arr)) return arr.some(pred);
  }
  return false;
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
  const { idToken, localId: uid } = await signInFirebase();
  await provisionUser(idToken);

  // Pick two slot-backed products: #1 for the order, #2 for the cart line.
  // Distinct products per surface so each /me/* page shows a different item.
  const picks = await pickSlotBackedProducts(idToken, 2);
  const [orderPick, cartPick] = picks;

  // Seed two favorites so the /me/favorites grid shows a multi-card row.
  await seedFavorite(idToken, orderPick.product.id);
  await seedFavorite(idToken, cartPick.product.id);

  const orderId = await seedOrder(idToken, uid, orderPick);
  console.log(`  order seeded: ${orderId}`);

  // Confirm the order via the dev-only mark-paid endpoint. Drives the same
  // production confirm path a Stripe webhook would, which credits points
  // through the existing PointEarnListener on OrderPaidEvent. Without this
  // the order stays PENDING forever and /me/points shows balance=0.
  await confirmOrderForDemo(idToken, orderId);

  // Seed a reservation request against reservation-service's dev seeder.
  // Uses the same venueId as orderPick so the seeder (which provisions
  // slots per product for the first 3 products) is guaranteed to have
  // populated something at that venue.
  const reservationId = await seedReservation(idToken, uid, orderPick.product.venueId);
  if (reservationId) console.log(`  reservation seeded: ${reservationId}`);

  // Post-seed assertion gates — warn (not fail) on transient listing miss.
  await assertListed(
    idToken,
    "/api/v1/me/orders",
    (body) =>
      hasContent(body, (row) => (row as { orderId?: string; id?: string }).orderId === orderId ||
        (row as { id?: string }).id === orderId),
    "orders",
  );
  await assertListed(
    idToken,
    "/api/v1/me/favorites",
    (body) => Array.isArray(body) && body.includes(orderPick.product.id),
    "favorites",
  );
  await assertListed(
    idToken,
    "/api/v1/me/reservations",
    (body) => Array.isArray(body) && body.length > 0,
    "reservations",
  );
  await assertListed(
    idToken,
    "/api/v1/me/points",
    (body) => {
      const b = (body as { balance?: number } | null)?.balance;
      return typeof b === "number" && b > 0;
    },
    "points",
  );

  const browser = await chromium.launch();
  const authContext = await newDesktopContext(browser);
  const anonContext = await newDesktopContext(browser);
  const mobileContext = await newMobileContext(browser);

  // Seed the cart into the auth + mobile contexts BEFORE any page opens,
  // so /ja/cart renders with lines on first navigation.
  const cartLine = buildCartLine(cartPick);
  const cartKey = `asoview:cart:${uid}`;
  const cartValue = JSON.stringify({ lines: [cartLine] });
  for (const ctx of [authContext, mobileContext]) {
    await ctx.addInitScript(
      ({ key, value }) => {
        try {
          localStorage.setItem(key, value);
        } catch {
          // Storage may be inaccessible pre-navigation; swallow.
        }
      },
      { key: cartKey, value: cartValue },
    );
  }

  // Mobile-only: prime visit count so <InstallPrompt> has a chance to
  // render. Best-effort; the shot does not depend on the banner firing.
  await mobileContext.addInitScript(() => {
    try {
      localStorage.setItem("pwa:visit-count", "2");
    } catch {
      // Ignored.
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
      const route = shot.route
        .replace("__PRODUCT_DETAIL__", `/ja/products/${orderPick.product.id}`)
        .replace("__TICKET_DETAIL__", `/ja/tickets/${orderId}`);
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
