# asoview-web demo video

Screenshot capture (Playwright) + 60s video composition (Remotion).

## Quickstart

```bash
cd tools/demo-video
bun install
bunx playwright install --with-deps chromium

export E2E_FIREBASE_API_KEY=...
export E2E_TEST_EMAIL=...
export E2E_TEST_PASSWORD=...
# Optional — defaults to https://asoview-clone-dev.duckdns.org
# export DEMO_BASE_URL=http://localhost:3000

bun run capture   # writes out/screenshots/*.png + out/shots.json
bun run render    # writes out/demo.mp4
```

`bun run build` runs both.

## Structure

- `shots.ts` — declarative shot list (route, caption, annotation selectors).
- `capture.ts` — Playwright script: signs in, seeds a favorite, visits each
  route, writes `out/shots.json` with PNG paths and annotation bounding boxes.
- `remotion/src/DemoVideo.tsx` — reads `out/shots.json`, sequences shots with
  crossfades, overlays callouts + caption pill.
- `remotion/src/Callout.tsx` — element-anchored callout with label. Label
  position is flipped if the `pointFrom` side would leave the frame.

## Side effects on the live dev site

Running `bun run capture` writes to the shared dev cluster as the test user:

- `PUT /v1/me/favorites/{productId}` × 2 — idempotent, no cleanup needed.
- `POST /v1/orders` with `Idempotency-Key = demo-video:{uid}:{variantId}:{slotId}`.
  The deterministic key dedupes on the server: repeated runs against the
  same user/product/slot reuse the existing PENDING order instead of
  creating a new row every run. No manual cleanup required.
- `localStorage["asoview:cart:{uid}"]` is written to the Playwright
  browser context, not the server. Not a side effect on dev.

## Tuning

- Durations are per-shot in `shots.ts` (sum ≈ 60s today).
- Viewport: 1280×800 (`VIEWPORT` in `shots.ts`). DPR=1 so screenshot pixels
  map 1:1 to `getBoundingClientRect()` and Remotion overlay coordinates.
- To re-annotate a shot, edit the selector/label in `shots.ts` and rerun
  `bun run capture`. Annotations that fail to resolve are logged and skipped.
