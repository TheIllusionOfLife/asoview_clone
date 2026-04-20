// Narrow, opt-in service worker.
//
// Only two behaviors are enabled; everything else passes through to the
// network with no caching. This intentionally avoids breaking App Router
// traffic patterns (Server Actions with `next-action` header, RSC prefetch
// with `next-router-*` headers + `Sec-Purpose: prefetch`, non-GET `/api/*`
// mutations, `keepalive` beacons, Stripe iframes, and Firebase `/__/auth/*`).
//
// See `docs/adr/003-pwa-hand-rolled-minimal-sw.md` for the full rationale.

const CACHE_NAME = "asoclone-shell-v1";
const LOCALES = ["ja", "en"];
const DEFAULT_LOCALE = "ja";
const OFFLINE_PATHS = LOCALES.map((loc) => `/${loc}/offline`);
const PRECACHE_URLS = [
  ...OFFLINE_PATHS,
  "/icons/icon-192.png",
  "/icons/icon-512.png",
  "/manifest.webmanifest",
];

function offlinePathForRequest(request) {
  try {
    const url = new URL(request.url);
    const segments = url.pathname.split("/").filter(Boolean);
    const first = segments[0];
    if (first && LOCALES.includes(first)) {
      return `/${first}/offline`;
    }
  } catch {
    // fall through
  }
  return `/${DEFAULT_LOCALE}/offline`;
}

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("asoclone-") && key !== CACHE_NAME)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;

  // Rule 0: non-GET is ALWAYS pass-through. Server Actions, API mutations,
  // keepalive/beacon POSTs must reach the network untouched.
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);

  // Rule 1: hashed immutable Next.js static assets → CacheFirst.
  if (url.origin === self.location.origin && url.pathname.startsWith("/_next/static/")) {
    event.respondWith(
      caches.match(request).then((cached) => {
        if (cached) return cached;
        return fetch(request).then((response) => {
          if (response.ok) {
            // Keep the SW alive until the cache write finishes. Without
            // `waitUntil`, the SW can be terminated mid-write and the
            // cache entry is lost; the next request re-fetches over
            // the network and never populates the cache.
            const clone = response.clone();
            event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.put(request, clone)));
          }
          return response;
        });
      }),
    );
    return;
  }

  // Rule 2: HTML navigation requests → NetworkOnly, fallback to the
  // pre-cached locale-specific /<locale>/offline when the network fails.
  // Never cache the navigation response itself, so live data, RSC
  // streams, and auth-dependent pages always reflect current server state.
  if (request.mode === "navigate") {
    const fallbackPath = offlinePathForRequest(request);
    event.respondWith(
      fetch(request).catch(async () => {
        const cached =
          (await caches.match(fallbackPath, { ignoreSearch: true })) ??
          (await caches.match(`/${DEFAULT_LOCALE}/offline`, { ignoreSearch: true }));
        return (
          cached ??
          new Response("Offline", {
            status: 503,
            headers: { "Content-Type": "text/plain; charset=utf-8" },
          })
        );
      }),
    );
    return;
  }

  // Everything else (RSC prefetch, `/api/*` GETs, Stripe, `/__/auth/*`,
  // images, fonts, etc.) falls through to the default network behavior.
});
