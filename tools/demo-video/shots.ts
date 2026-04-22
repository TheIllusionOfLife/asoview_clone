import type { Page } from "@playwright/test";

export type Annotation = {
  selector: string;
  label: string;
  pointFrom: "top" | "bottom" | "left" | "right";
  tone?: "info" | "highlight";
};

export type Shot = {
  id: string;
  route: string;
  durationSec: number;
  caption: string;
  requiresAuth?: boolean;
  waitFor?: { selector?: string; timeoutMs?: number };
  preCapture?: (page: Page) => Promise<void>;
  annotations?: Annotation[];
};

export type ShotManifestEntry = {
  id: string;
  image: string;
  durationSec: number;
  caption: string;
  annotations: Array<Annotation & { x: number; y: number; width: number; height: number }>;
};

export type ShotManifest = {
  viewport: { width: number; height: number };
  dpr: number;
  fps: number;
  shots: ShotManifestEntry[];
};

export const VIEWPORT = { width: 1280, height: 800 } as const;
export const DPR = 1;
export const FPS = 30;

// Ordered shot list. Durations tuned for ~60s total when summed with crossfades.
export const SHOTS: Shot[] = [
  {
    id: "01-home-hero",
    route: "/ja",
    durationSec: 4.0,
    caption: "AsoClone — discover local experiences",
    waitFor: { selector: "h1" },
    annotations: [
      { selector: "h1", label: "Hero headline", pointFrom: "bottom", tone: "highlight" },
      {
        selector: "[aria-labelledby='areas-heading']",
        label: "Browse by region",
        pointFrom: "top",
      },
    ],
  },
  {
    id: "02-search-text",
    route: "/ja/search?query=%E3%82%AD%E3%83%A3%E3%83%B3%E3%83%97",
    durationSec: 3.5,
    caption: "Full-text search",
    waitFor: { selector: "main" },
    annotations: [
      {
        selector: "input[type='search'], input[aria-label*='search' i]",
        label: "Search bar",
        pointFrom: "bottom",
        tone: "highlight",
      },
    ],
  },
  {
    id: "03-search-facets",
    route: "/ja/search?query=&priceMax=5000&sort=price_asc",
    durationSec: 4.0,
    caption: "Filter by price & sort",
    waitFor: { selector: "main" },
    annotations: [
      {
        selector: "select, input[inputmode='numeric']",
        label: "Facet controls",
        pointFrom: "top",
        tone: "highlight",
      },
    ],
  },
  {
    id: "04-area-landing",
    route: "/ja/area/hokkaido",
    durationSec: 3.5,
    caption: "Regional landing pages",
    waitFor: { selector: "h1" },
  },
  {
    id: "05-product-detail",
    route: "__PRODUCT_DETAIL__",
    durationSec: 6.0,
    caption: "Live slot availability",
    waitFor: { selector: "[role='radiogroup']" },
    annotations: [
      { selector: "h1", label: "Activity", pointFrom: "bottom", tone: "info" },
      {
        selector: "[role='radiogroup']",
        label: "Pick a date + time",
        pointFrom: "left",
        tone: "highlight",
      },
    ],
  },
  {
    id: "06-signin",
    route: "/ja/signin",
    durationSec: 3.0,
    caption: "Sign in with email or Google",
    requiresAuth: false,
    waitFor: { selector: "h1" },
    annotations: [
      {
        selector: "button:has-text('Google'), button:has-text('グーグル')",
        label: "Google OAuth",
        pointFrom: "right",
        tone: "highlight",
      },
    ],
  },
  {
    id: "07-product-favorited",
    route: "__PRODUCT_DETAIL__",
    durationSec: 4.0,
    caption: "Favorites sync across sessions",
    requiresAuth: true,
    waitFor: { selector: "h1" },
    annotations: [
      {
        selector: "button[aria-label*='avorite' i], button[aria-label*='お気に入り']",
        label: "Favorite toggle",
        pointFrom: "bottom",
        tone: "highlight",
      },
    ],
  },
  {
    id: "08-product-reviews",
    route: "__PRODUCT_DETAIL__",
    durationSec: 4.5,
    caption: "Reviews from past guests",
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
        label: "Reviews + ratings",
        pointFrom: "right",
        tone: "highlight",
      },
    ],
  },
  {
    id: "09-me-reservations",
    route: "/ja/me/reservations",
    durationSec: 3.5,
    caption: "Your reservations",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "10-me-favorites",
    route: "/ja/me/favorites",
    durationSec: 4.0,
    caption: "Your favorites",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "11-me-orders",
    route: "/ja/me/orders",
    durationSec: 3.5,
    caption: "Order history",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "12-me-points",
    route: "/ja/me/points",
    durationSec: 3.5,
    caption: "Points wallet",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "13-cart",
    route: "/ja/cart",
    durationSec: 4.5,
    caption: "Cart with totals",
    requiresAuth: true,
    waitFor: { selector: "main" },
  },
  {
    id: "14-home-closing",
    route: "/ja",
    durationSec: 4.0,
    caption: "Installable PWA · built on GCP",
    waitFor: { selector: "h1" },
    annotations: [
      { selector: "h1", label: "AsoClone", pointFrom: "bottom", tone: "highlight" },
    ],
  },
];
