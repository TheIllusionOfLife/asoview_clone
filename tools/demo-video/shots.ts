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

// Ordered 13-shot list. Durations tuned for ~60s total when summed with
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
    // Merged query + facets: キャンプ text search + priceMax filter + price-asc sort.
    route: "/ja/search?query=%E3%82%AD%E3%83%A3%E3%83%B3%E3%83%97&priceMax=5000&sort=price_asc",
    durationSec: 5.0,
    caption: "検索と絞り込み",
    waitFor: { selector: "main" },
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
    waitFor: { selector: "h1" },
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
    waitFor: { selector: "main" },
  },
  {
    id: "08-me-orders",
    kind: "capture",
    route: "/ja/me/orders",
    durationSec: 4.5,
    caption: "注文履歴",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "09-me-reservations",
    kind: "capture",
    route: "/ja/me/reservations",
    durationSec: 4.5,
    caption: "予約履歴",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "10-me-favorites",
    kind: "capture",
    route: "/ja/me/favorites",
    durationSec: 4.5,
    caption: "お気に入り",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "11-mobile-ux",
    kind: "capture",
    route: "/ja",
    durationSec: 4.5,
    caption: "モバイル対応",
    context: "mobile",
    waitFor: { selector: "h1" },
    annotations: [
      {
        selector: "h1",
        label: "スマホ最適化",
        pointFrom: "bottom",
        tone: "highlight",
      },
    ],
  },
  {
    id: "12-architecture",
    kind: "prerendered",
    component: "ArchitectureSlide",
    durationSec: 7.0,
    caption: "Google Cloud で構築",
    // Coordinates reference ArchitectureSlide's fixed layout (see
    // remotion/src/ArchitectureSlide.tsx). Edit there + here together.
    annotations: [
      { label: "GKE", x: 340, y: 170, width: 600, height: 220, pointFrom: "top", tone: "highlight" },
      { label: "Spanner", x: 760, y: 520, width: 180, height: 80, pointFrom: "top" },
      { label: "Firebase Auth", x: 80, y: 520, width: 200, height: 80, pointFrom: "top" },
      { label: "Vertex AI Search", x: 310, y: 520, width: 220, height: 80, pointFrom: "top" },
    ],
  },
  {
    id: "13-home-closing",
    kind: "capture",
    route: "/ja",
    durationSec: 4.0,
    caption: "AsoClone",
    waitFor: { selector: "h1" },
    annotations: [
      { selector: "h1", label: "AsoClone", pointFrom: "bottom", tone: "highlight" },
    ],
  },
];
