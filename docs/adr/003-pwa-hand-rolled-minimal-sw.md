# ADR 003: Hand-rolled minimal service worker for asoview-web PWA

## Status

Accepted — 2026-04-21, shipped in PR `feat/asoview-web-pwa` (4b).

## Context

PR 4b turns `apps/asoview-web` into an installable PWA: manifest, icons,
service worker, install prompt, offline fallback. asoview-web runs on
Next.js 16 App Router with `output: "standalone"`, Firebase Auth via
`/__/auth/*`, Stripe Elements in iframes, Server Actions, and RSC
prefetching. Any service worker that caches too broadly breaks one of
those flows silently.

## Decision

Ship a **hand-rolled ~50-line service worker** with an explicit
allowlist of two behaviors; everything else passes through to the
network untouched.

1. **CacheFirst for `/_next/static/*`** — hashed, immutable, safe to
   cache indefinitely.
2. **NetworkOnly with offline fallback for `request.mode === 'navigate'`**
   — if the network fails, serve the pre-cached `/offline` shell.
   Navigation responses themselves are NEVER cached, so live data, RSC
   streams, and auth-dependent pages always reflect current server state.

Everything else — non-GET requests, `/api/*` GETs, RSC prefetches,
Server Actions, `keepalive` beacons, Stripe iframes, `/__/auth/*`,
images, fonts — falls through via no `event.respondWith(...)`, letting
the browser handle it normally.

The rollback is a second committed worker, `sw-unregister.js`, swapped
into `sw.js` to actively `unregister()` and purge caches. A 404 is not
a valid rollback.

## Alternatives considered

### next-pwa

Abandoned upstream. Last meaningful release predates App Router; the
maintainer has flagged it as unmaintained. Its runtime caching presets
assume Pages Router and would conflict with RSC traffic.

### Serwist

Actively maintained and modern. Its value is in runtime caching
abstractions (NetworkFirst, StaleWhileRevalidate, etc.) with plug-in
strategies. This PR ships neither runtime API caching nor image
caching, so Serwist's abstractions would be dead weight for now.
Upgrade path: **adopt Serwist when runtime caching is on the roadmap**
(expected when user reports show slow-network pain).

### Broad runtime caching now (NetworkFirst on `/api/*` +
StaleWhileRevalidate on Unsplash)

Rejected. Blocking review findings enumerated:

- Non-GET `/api/*` mutations would be intercepted unless explicitly
  gated, and a missed gate silently breaks favorites, points, reviews.
- RSC prefetch has its own header-set (`rsc`, `next-router-prefetch`,
  `next-router-state-tree`, `next-router-segment-prefetch`, `next-url`,
  `Sec-Purpose: prefetch`). Caching it inverts staleness semantics.
- Server Actions POST to arbitrary route paths with a `next-action`
  header. Caching would return stale HTML + desync the client-side
  state tree.
- `keepalive` POST beacons must not be held by a SW fetch handler.
- `cache: 'only-if-cached'` edge cases interact badly with CacheFirst
  fallbacks and produce opaque errors.

Installability (the goal of this PR) does not require runtime caching;
the spec needs a manifest + icons + HTTPS + a registered SW. Shipping
runtime caching later, under the Serwist umbrella with proper tests,
is the right ordering.

## Consequences

### Positive

- First-load and repeat-load behavior is identical to the non-PWA app
  except for (a) the install banner and (b) the `/offline` fallback on
  a failed navigation. Zero risk of silent breakage on mutations or
  RSC.
- The SW code is short enough to audit in one sitting and doesn't rely
  on a third-party build step or dependency.
- The unregister worker gives us a real rollback path; we don't have
  to cross fingers that a 404 clears user state.
- Pattern is copy-ready for `urakata-ticket-web` (natural "ticket
  wallet" PWA).

### Negative

- No offline browsing of product pages or cached API data. Acceptable
  for a study clone; revisit if user reports demand it.
- No push notifications (requires separate FCM wire-up; explicitly out
  of scope for 4b).
- We accept a single cache version (`asoclone-shell-v1`) and must bump
  it deliberately when pre-cache contents change.

## Implementation notes

- SW lives at `apps/asoview-web/public/sw.js` so `output: "standalone"`
  copies it straight into the image without a separate webpack entry.
- Registration happens in `<SWRegister />` mounted in
  `apps/asoview-web/src/app/[locale]/layout.tsx`, gated on
  `process.env.NODE_ENV === "production"` and
  `"serviceWorker" in navigator`.
- Install prompt listener is attached at module load, not in a
  `useEffect`, because Chrome may fire `beforeinstallprompt` before
  React hydrates.
- Capability detection (`matchMedia('(display-mode: standalone)')` +
  `navigator.standalone`) runs before the UA-based iOS fallback to
  avoid showing a banner to users who already installed the PWA.
- CSP gains `manifest-src 'self'` and `worker-src 'self'` in
  `apps/asoview-web/src/lib/csp.ts`. No other CSP loosening.

## Upgrade path

When we adopt runtime caching (API GETs, Unsplash images) or push
notifications:

1. Replace `public/sw.js` with a Serwist-generated worker under a new
   cache name.
2. Keep `sw-unregister.js` around as the rollback.
3. Revisit this ADR with a follow-up noting which strategies apply to
   which URL patterns; document the expected staleness for each.
