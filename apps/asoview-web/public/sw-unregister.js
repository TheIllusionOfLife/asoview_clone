// Rollback worker. Kept alongside sw.js but never served by default.
// Rollback procedure: swap `sw.js` to serve THIS file's contents (via a
// Next.js rewrite or a one-line patch) and deploy. On next navigation,
// every client's existing SW activates this handler, unregisters itself,
// and purges all `asoclone-*` caches. Keep the swap in place for ≥24h
// so all sessions pick it up.
//
// A 404 on /sw.js alone does NOT unregister an active worker — browsers
// keep running the previously-installed worker. This file is the only
// supported rollback path.

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => k.startsWith("asoclone-")).map((k) => caches.delete(k)));
      await self.registration.unregister();
      const clients = await self.clients.matchAll({ type: "window" });
      for (const client of clients) {
        // Force reload so the page picks up live server behavior
        // instead of a cached shell served by the previous worker.
        client.navigate(client.url).catch(() => {});
      }
    })(),
  );
});

self.addEventListener("fetch", () => {
  // Pass everything through. Do not serve any cached responses.
});
