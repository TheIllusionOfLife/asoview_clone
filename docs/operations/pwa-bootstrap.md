# PWA bootstrap runbook

Operational notes for the asoview-web PWA layer (manifest + service worker
+ install prompt). See `docs/adr/003-pwa-hand-rolled-minimal-sw.md` for
the scope decisions.

## Verify a fresh deploy

After any deploy that changes `apps/asoview-web/public/sw.js`, the
manifest, or the install prompt:

1. Visit `https://asoview-clone-dev.duckdns.org/ja` in Chrome (Android
   emulation on). Open DevTools → **Application**.
2. **Manifest** tab: `name = AsoClone`, no warnings, "Installable" badge.
3. **Service Workers** tab: `sw.js` listed, scope `/`, status "activated
   and is running".
4. **Cache storage** → `asoclone-shell-v1` contains:
   - `/offline`
   - `/icons/icon-192.png`
   - `/icons/icon-512.png`
   - `/manifest.webmanifest`
5. Switch **Network** tab to "Offline". Reload any route. The offline
   page must appear with a working Retry control.
6. Back online. Trigger a mutation (e.g. favorite a product, submit a
   review). Confirm the request hits the network (200 in the Network
   tab); the SW must NEVER intercept non-GET traffic.

## iOS install walkthrough

1. Open Safari on iPhone/iPad.
2. Navigate to the dev URL above.
3. Second visit: the iOS info banner appears once, then never again
   (keyed on `localStorage["pwa:install-outcome"]`).
4. Use Safari's Share → "Add to Home Screen". An AsoClone icon lands
   on the home screen. Tapping it launches the app in a standalone
   window (no Safari chrome).
5. Confirm capability detection: on the installed PWA, no install
   banner ever renders (`display-mode: standalone` matches and
   `navigator.standalone` is `true`).

## Android/Chrome install walkthrough

1. Chrome (Android or desktop Chrome with device emulation).
2. Load the dev URL twice. The `beforeinstallprompt` event fires, the
   module-level listener stashes it, and on the second visit the banner
   appears.
3. Tap "Install" → Chrome's native install dialog appears → accept.
4. App launches in a standalone window. Re-visiting the dev URL in a
   normal Chrome tab no longer shows the banner (outcome persisted).

## Bump the service worker

When `sw.js` ships a meaningful change (new pre-cache entries, new
fetch rules), bump the cache version:

```js
const CACHE_NAME = "asoclone-shell-v2"; // was v1
```

The activate handler deletes any cache whose name starts with
`asoclone-` and is not the current name, so the old cache is purged
automatically within 24h or at next hard refresh.

No other changes are required — clients pick up the new worker on the
next navigation and `skipWaiting` + `clients.claim()` switch them over
without an extra reload.

## Rollback

If the PWA breaks production (white screens, bad cache, stuck install
loop), the ONLY supported rollback is to ship the unregister worker.

Steps:

1. In `apps/asoview-web/public/`, copy `sw-unregister.js` on top of
   `sw.js` (preserve the unregister file so a future re-enable is
   reproducible):

   ```bash
   cp apps/asoview-web/public/sw-unregister.js apps/asoview-web/public/sw.js
   ```

2. Commit and deploy. The unregister worker replaces the active SW on
   the next navigation, calls `self.registration.unregister()`, and
   purges every `asoclone-*` cache.
3. Keep the unregister worker deployed for at least one full release
   cycle (≥24h) so all sessions get a chance to activate it.
4. Once rollout is complete, either remove `/sw.js` entirely or ship a
   fresh SW under a new cache name.

**Do NOT** rollback by deleting `/sw.js` to 404. Browsers keep the
previously-installed worker running; a 404 leaves users indefinitely on
the stale cached shell. Only the unregister-worker swap above clears
existing registrations.

## Clearing a user's local state for debugging

If a single user reports a broken PWA and you can't reproduce:

1. DevTools → **Application** → **Storage** → "Clear site data".
2. Or in the DevTools console:
   ```js
   (await navigator.serviceWorker.getRegistrations()).forEach((r) => r.unregister());
   (await caches.keys()).forEach((k) => caches.delete(k));
   localStorage.removeItem("pwa:visit-count");
   localStorage.removeItem("pwa:install-outcome");
   ```
3. Hard-reload. The next page load re-registers the current SW fresh.
