import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { chromium, type Page } from "@playwright/test";
import { DPR, FPS, VIEWPORT } from "./shots.js";
import type { ShotManifest, ShotManifestEntry } from "./shots.js";

// Operator (UraKata Reservation) demo-video capture. Separate from the
// consumer flow in capture.ts so the two manifests, output MP4s, and
// browser contexts don't interfere. The authenticated user must already
// hold { admin:true, tenantId:<OPERATOR_TENANT_ID> } Firebase claims
// (see scripts/provision-operator-claim.sh — the PR that ships it is
// still in flight at the time of writing, but e2e-test-2 has been
// manually provisioned for this capture).
const BASE_URL = process.env.OP_BASE_URL ?? "https://asoview-operator.duckdns.org";
const VENUE_ID = process.env.OP_VENUE_ID ?? "84d9e262-94d6-5e63-a482-f8839d2741b0";
const RESERVATION_ID =
  process.env.OP_RESERVATION_ID ?? "7edb7cb9-45ab-4c4b-809d-720790ec9662";
const TEST_EMAIL = mustEnv("E2E_TEST_EMAIL");
const TEST_PASSWORD = mustEnv("E2E_TEST_PASSWORD");
const OUT_DIR = join(import.meta.dirname, "out");
const SHOTS_DIR = join(OUT_DIR, "operator-screenshots");

function mustEnv(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Missing env var: ${name}`);
  return v;
}

type OperatorShot = {
  id: string;
  route: string;
  durationSec: number;
  caption: string;
  waitFor: string;
  selectVenue: boolean;
};

const SHOTS: OperatorShot[] = [
  {
    id: "op-01-dashboard",
    route: "/ja",
    durationSec: 5.0,
    caption: "ダッシュボード: 予約状況と稼働率を一目で",
    // Wait for the util-percent bar, which only renders once the dashboard
    // fetch resolves with a selected venue.
    waitFor: "text=スロット利用率",
    selectVenue: true,
  },
  {
    id: "op-02-slots",
    route: "/ja/slots",
    durationSec: 5.0,
    caption: "スロット管理: 容量・時間帯を編集",
    // Capacity row "X/Y" plus a percentage — rendered per slot card.
    waitFor: "text=/\\d+\\/\\d+/",
    selectVenue: true,
  },
  {
    id: "op-03-reservations",
    route: "/ja/reservations",
    durationSec: 5.0,
    caption: "予約一覧: ステータス別に管理",
    // Seeded "デモ太郎" rows exist for venue 84d9e2…
    waitFor: "text=デモ太郎",
    selectVenue: true,
  },
  {
    id: "op-04-reservation-detail",
    // Detail page doesn't use the venue selector; goes straight by id.
    route: `/ja/reservations/${RESERVATION_ID}`,
    durationSec: 5.0,
    caption: "予約詳細: 承認・却下・キャンセル",
    waitFor: "text=予約詳細",
    selectVenue: false,
  },
];

async function signInViaUI(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/ja/login`);
  // The login form uses <input id="email"> / <input id="password"> and
  // a single submit button. No data-testid hooks here.
  await page.locator("#email").fill(TEST_EMAIL);
  await page.locator("#password").fill(TEST_PASSWORD);
  await page.locator("button[type='submit']").click();
  // After auth the route replaces to "/". Wait for anything that proves
  // we're not on /login anymore.
  await page.waitForURL((u) => !u.toString().includes("/login"), { timeout: 20_000 });
}

async function selectVenue(page: Page): Promise<void> {
  // VenueSelector uses <select aria-label="施設を選択"> with options whose
  // value IS the venue UUID (the label is also the UUID; see
  // components/VenueSelector.tsx — option label === venueId).
  const selector = page.locator("select[aria-label='施設を選択']").first();
  await selector.waitFor({ state: "visible", timeout: 10_000 });
  await selector.selectOption({ value: VENUE_ID });
}

async function captureShot(
  page: Page,
  shot: OperatorShot,
): Promise<ShotManifestEntry> {
  const url = `${BASE_URL}${shot.route}`;
  console.log(`  → ${shot.id} :: ${url}`);
  await page.goto(url, { waitUntil: "domcontentloaded" });

  if (shot.selectVenue) {
    await selectVenue(page);
  }

  await page
    .locator(shot.waitFor)
    .first()
    .waitFor({ state: "visible", timeout: 15_000 });

  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(500);

  const filename = `${shot.id}.png`;
  await page.screenshot({
    path: join(SHOTS_DIR, filename),
    fullPage: false,
    animations: "disabled",
    clip: { x: 0, y: 0, width: VIEWPORT.width, height: VIEWPORT.height },
  });

  return {
    id: shot.id,
    image: filename,
    durationSec: shot.durationSec,
    caption: shot.caption,
    annotations: [],
  };
}

async function main() {
  await mkdir(SHOTS_DIR, { recursive: true });
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Venue: ${VENUE_ID}`);

  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: DPR,
    locale: "ja-JP",
    timezoneId: "Asia/Tokyo",
  });
  try {
    const page = await context.newPage();
    await signInViaUI(page);
    const manifest: ShotManifest = {
      viewport: VIEWPORT,
      dpr: DPR,
      fps: FPS,
      shots: [],
    };
    for (const shot of SHOTS) {
      const entry = await captureShot(page, shot);
      manifest.shots.push(entry);
    }

    const manifestPath = join(OUT_DIR, "operator.shots.json");
    await writeFile(manifestPath, JSON.stringify(manifest, null, 2));

    const inlined: ShotManifest = {
      ...manifest,
      shots: await Promise.all(
        manifest.shots.map(async (s) => {
          const bytes = await readFile(join(SHOTS_DIR, s.image));
          return { ...s, image: `data:image/png;base64,${bytes.toString("base64")}` };
        }),
      ),
    };
    await writeFile(join(OUT_DIR, "operator.shots.inline.json"), JSON.stringify(inlined));
    console.log(`Wrote ${manifest.shots.length} shots to ${OUT_DIR}`);
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
