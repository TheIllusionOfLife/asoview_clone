import type { Page } from "@playwright/test";

export type Annotation = {
  selector: string;
  label: string;
  pointFrom: "top" | "bottom" | "left" | "right";
  tone?: "info" | "highlight";
};

// Pre-resolved annotation for shots that don't originate from a Playwright
// capture (e.g. the Remotion-rendered architecture slide). The coordinates
// are hand-authored against the component's fixed layout.
export type PrerenderedAnnotation = {
  label: string;
  pointFrom: "top" | "bottom" | "left" | "right";
  tone?: "info" | "highlight";
  x: number;
  y: number;
  width: number;
  height: number;
};

type CaptureKind = {
  kind: "capture";
  route: string;
  requiresAuth?: boolean;
  /** Which browser context to use. "auth" = authenticated desktop (default),
   *  "anon" = unauthenticated desktop, "mobile" = iPhone 14 authenticated. */
  context?: "auth" | "anon" | "mobile";
  waitFor?: { selector?: string; timeoutMs?: number };
  preCapture?: (page: Page) => Promise<void>;
  annotations?: Annotation[];
};

type PrerenderedKind = {
  kind: "prerendered";
  /** Name of the Remotion component to render (e.g. "ArchitectureSlide"). */
  component: string;
  annotations?: PrerenderedAnnotation[];
};

export type Shot = {
  id: string;
  durationSec: number;
  caption: string;
} & (CaptureKind | PrerenderedKind);

// Manifest entries the renderer reads. For capture shots the image is a
// base64 data URL; for prerendered shots the `image` field is empty and
// `component` names the React component to render in its place.
export type ShotManifestEntry = {
  id: string;
  image: string;
  component?: string;
  durationSec: number;
  caption: string;
  annotations: PrerenderedAnnotation[];
};

export type ShotManifest = {
  viewport: { width: number; height: number };
  dpr: number;
  fps: number;
  shots: ShotManifestEntry[];
};

export const VIEWPORT = { width: 1280, height: 800 } as const;
export const MOBILE_VIEWPORT = { width: 390, height: 844 } as const;
export const DPR = 1;
export const FPS = 30;

// Ordered 13-shot list. Durations tuned for ~57s total when summed with
// crossfades. Japanese captions + annotations per revised plan.
export const SHOTS: Shot[] = [
  {
    id: "01-home-hero",
    kind: "capture",
    route: "/ja",
    durationSec: 4.5,
    caption: "AsoClone — 地域の体験を見つけよう",
    waitFor: { selector: "h1" },
    annotations: [
      { selector: "h1", label: "トップ", pointFrom: "bottom", tone: "highlight" },
      {
        selector: "[aria-labelledby='areas-heading']",
        label: "エリアから探す",
        pointFrom: "top",
      },
    ],
  },
  {
    id: "02-search",
    kind: "capture",
    // Show the catalog with a priceMax filter + price-asc sort. Dropped the
    // text query ("キャンプ" matched nothing against the English-titled
    // dev catalog, producing an empty result frame).
    route: "/ja/search?priceMax=5000&sort=price_asc",
    durationSec: 5.0,
    caption: "検索と絞り込み",
    // Wait for the first result row — search results are text-only <li>
    // with h3 (no images).
    waitFor: { selector: "ul li h3" },
    annotations: [
      {
        selector: "input[type='search'], input[aria-label*='search' i]",
        label: "検索バー",
        pointFrom: "bottom",
        tone: "highlight",
      },
      {
        selector: "select, input[inputmode='numeric']",
        label: "絞り込み",
        pointFrom: "top",
      },
    ],
  },
  {
    id: "03-area-landing",
    kind: "capture",
    // Route was /area/ (singular) in v1 — the real page is /areas/ (plural).
    route: "/ja/areas/hokkaido",
    durationSec: 3.5,
    caption: "エリアから探す",
    // Wait for the subtitle that only the success path renders
    // ("{N}件の体験が見つかりました"). error.tsx renders its own <h1>
    // ("問題が発生しました") but not this string, so a fallback to the
    // h1 selector would silently match the error boundary.
    waitFor: { selector: "text=件の体験が見つかりました" },
    annotations: [{ selector: "h1", label: "北海道の体験", pointFrom: "bottom" }],
  },
  {
    id: "04-signin",
    kind: "capture",
    route: "/ja/signin",
    durationSec: 3.0,
    caption: "メール/Googleでログイン",
    context: "anon",
    waitFor: { selector: "h1" },
    annotations: [
      {
        selector: "button:has-text('Google'), button:has-text('グーグル')",
        label: "Googleでログイン",
        pointFrom: "right",
        tone: "highlight",
      },
    ],
  },
  {
    // Merged: product detail post-auth. The heart is filled (favorite was
    // seeded) AND the slot picker is visible in the right pane, so the
    // annotation set covers both "favorites sync" and "live slot availability"
    // without needing two separate frames.
    id: "05-product-detail",
    kind: "capture",
    route: "__PRODUCT_DETAIL__",
    durationSec: 6.0,
    caption: "予約とお気に入り",
    requiresAuth: true,
    waitFor: { selector: "[role='radiogroup']" },
    annotations: [
      {
        selector: "[role='radiogroup']",
        label: "日時を選択",
        pointFrom: "left",
        tone: "highlight",
      },
      {
        selector: "button[aria-label*='avorite' i], button[aria-label*='お気に入り']",
        label: "お気に入り",
        pointFrom: "bottom",
      },
    ],
  },
  {
    id: "06-product-reviews",
    kind: "capture",
    route: "__PRODUCT_DETAIL__",
    durationSec: 4.0,
    caption: "レビューと評価",
    requiresAuth: true,
    waitFor: { selector: "h1" },
    preCapture: async (page) => {
      await page
        .locator("h2")
        .filter({ hasText: /レビュー|Reviews/ })
        .first()
        .scrollIntoViewIfNeeded()
        .catch(() => {});
    },
    annotations: [
      {
        selector: "h2:has-text('レビュー')",
        label: "レビュー",
        pointFrom: "right",
        tone: "highlight",
      },
    ],
  },
  {
    id: "07-cart",
    kind: "capture",
    route: "/ja/cart",
    durationSec: 4.5,
    caption: "カート・合計金額",
    requiresAuth: true,
    // Wait for the checkout button (only renders when cart has at
    // least one line) so the screenshot never catches a pre-hydration
    // empty state.
    waitFor: { selector: "text=購入手続きへ" },
  },
  {
    id: "08-me-orders",
    kind: "capture",
    route: "/ja/me/orders",
    durationSec: 4.0,
    // The app's JA nav translates "orders" as 予約履歴 (booking history);
    // the mark-paid seeder flips the PENDING row to PAID so the pill reads
    // 決済済み rather than 未決済 in this frame.
    caption: "予約履歴",
    requiresAuth: true,
    // Wait for a status pill — MyOrdersClient maps PAID -> "予約済み"
    // and PENDING -> "未決済", so matching either ensures the shot
    // captures whether or not the dev mark-paid flow completed.
    waitFor: { selector: "text=/予約済み|未決済/" },
  },
  {
    id: "09-me-reservations",
    kind: "capture",
    route: "/ja/me/reservations",
    durationSec: 3.5,
    // App nav translates reservations as 予約リクエスト; distinct from
    // /me/orders (予約履歴) so the two shots don't caption-collide.
    caption: "予約リクエスト",
    requiresAuth: true,
    // Wait for either the seeded row (guestName="デモ太郎") OR the
    // empty-state text. Both indicate the async list fetch resolved,
    // so the shot won't catch the "読み込み中…" loading state nor time
    // out when the seeder hasn't populated this user yet.
    waitFor: { selector: "text=/デモ太郎|予約リクエストはまだありません/" },
  },
  {
    id: "10-me-favorites",
    kind: "capture",
    route: "/ja/me/favorites",
    durationSec: 4.0,
    caption: "お気に入り",
    requiresAuth: true,
    // Wait for the hydrated ProductCard images, not just <main> — the
    // bare <main> renders instantly but the cards arrive after two
    // async fetches (favorites list, then product details).
    waitFor: { selector: "img.object-cover" },
  },
  {
    id: "11-me-points",
    kind: "capture",
    route: "/ja/me/points",
    durationSec: 3.5,
    caption: "ポイント残高",
    requiresAuth: true,
    // Wait for the balance label so the shot renders whether or not
    // points have been credited. The mark-paid demo path tries to
    // credit points but is fragile; this selector does not depend on
    // that succeeding.
    waitFor: { selector: "text=保有ポイント" },
  },
  {
    // Ticket detail — the post-payment screen. Shows the QR code that a
    // phone camera can scan, plus Apple/Google Wallet buttons. Tells the
    // "your phone is your ticket" story without needing a phone-frame
    // mockup or mobile emulation. Replaces the old 12-mobile-ux shot
    // (whose phone-frame rendering was visually broken and whose framing
    // — "this app is responsive" — was stale).
    id: "12-tickets",
    kind: "capture",
    route: "__TICKET_DETAIL__",
    durationSec: 5.0,
    caption: "QRで入場・スマホで見せる",
    requiresAuth: true,
    // TicketCard renders <img alt="QR code for ticket {id}"> only when
    // the TicketPass is in the "active" phase (validFrom <= now < validUntil).
    // Waiting on this alt text ensures the lazy `qrcode` import resolved
    // AND we're not staring at the before/expired text-pill variant.
    waitFor: { selector: "img[alt*='QR code']" },
    annotations: [
      {
        selector: "img[alt*='QR code']",
        label: "QRコードで入場",
        pointFrom: "left",
        tone: "highlight",
      },
      {
        selector: "button:has-text('Apple'), button:has-text('Google')",
        label: "Apple/Googleウォレットに保存",
        pointFrom: "right",
      },
    ],
  },
  {
    id: "13-architecture",
    kind: "prerendered",
    component: "ArchitectureSlide",
    durationSec: 7.0,
    caption: "Google Cloud で構築",
    // Annotations point at the FLOWS not the boxes — the boxes already have
    // labels, so another label on top of them would be redundant. Each
    // annotation explains *why* that edge exists.
    // Coordinates reference ArchitectureSlide's fixed layout (see
    // remotion/src/ArchitectureSlide.tsx). Edit there + here together.
    annotations: [
      // Central GKE cluster highlight box (no overlap with labels inside)
      {
        label: "モジュラーモノリス",
        x: 340,
        y: 170,
        width: 600,
        height: 40,
        pointFrom: "top",
        tone: "highlight",
      },
      // Spanner = strong-consistency data store
      {
        label: "強整合インベントリ",
        x: 760,
        y: 520,
        width: 180,
        height: 80,
        pointFrom: "bottom",
      },
      // Vertex AI Search = product search
      {
        label: "50商品を全文検索",
        x: 310,
        y: 520,
        width: 220,
        height: 80,
        pointFrom: "bottom",
      },
    ],
  },
];
