# PR 3d — asoview-web frontend foundation + booking funnel

Delivers the frontend foundation and the end-to-end booking funnel for
`apps/asoview-web` against the contracts frozen in #19/#20/#21.

## Summary

- Tailwind v4 + Biome + Playwright + Vitest scaffolding.
- Firebase init with `inMemoryPersistence` + Auth Emulator wiring.
- AuthProvider, sign-in flow, sanitized `?next=` redirect guard.
- Hand-typed API client: bearer auth, `Idempotency-Key`, `Page<T>` envelope,
  typed errors (`SignInRedirect`, `SlotTakenError`, `ApiError`, `NetworkError`),
  jittered backoff retry on idempotent GETs, `AbortController` plumbing.
- Visual foundation: Fraunces + Noto Sans JP via `next/font`, CSS tokens,
  Header/Footer/Skeleton, branded error.tsx + not-found.tsx.
- Pages: `/`, `/areas/[area]`, `/products/[id]` (SlotPicker), `/cart`,
  `/checkout/[orderId]`, `/tickets/[orderId]`, `/me/orders`.
- Persistent user-scoped cart (`localStorage`, guest-merge on signin).
- `/checkout/[orderId]`: Stripe Elements (lazy-loaded), PayPay redirect button,
  terminal-state polling (PAID/CANCELLED/FAILED + 30s timeout), idempotent
  intent replay, env-gated `?fakeMode=1` harness for CI Playwright runs.
- `/tickets/[orderId]`: lazy-loaded `qrcode` rendering with validFrom/validUntil
  gating in `Asia/Tokyo` (before/inside/after).
- `/me/orders`: client-side paginated list (backend returns flat List today),
  status badges, status-aware row links.
- CSP + HSTS + Referrer-Policy + Permissions-Policy in `next.config.ts`,
  `scripts/check-headers.sh` smokes via `curl -I`.
- Playwright matrix: auth/redirect, envelope shape + `Idempotency-Key`
  propagation, validity-window gating, cross-user 404, error UX. The
  funnel-happy and intent-replay backend-driven specs gate on
  `ASOVIEW_E2E_BACKEND=1`.
- New CI job `asoview-web-e2e` builds the app, smokes security headers,
  and runs the Playwright matrix.

## Backend contract discoveries (Session D)

- The payment-intent endpoint is `POST /v1/orders/{orderId}/payments`,
  not `/v1/payments`. Returns `{ paymentId, status, providerPaymentId,
  clientSecret }`. Idempotent on `orderId`.
- `GET /v1/me/orders` returns a flat `List<OrderResponse>`, not a Spring
  `Page<T>`. `/me/orders` paginates client-side; swap to `?page=` when
  the backend grows server-side pagination.
- There is no `GET /v1/orders/{orderId}/tickets`. The ticket endpoint is
  `GET /v1/me/tickets?orderId=...` which returns `200 []` for cross-user
  (filtered list, not a sub-resource). `/tickets/[orderId]` renders the
  empty list as "not found" so foreign-order existence does not leak.
- There is no fake-driver webhook endpoint in commerce-core. The fakeMode
  harness skips Stripe entirely and just polls; CI must run commerce-core
  with `payments.gateway=fake` (FakePaymentGateway is currently test-scope
  only — promoting it to main scope or wiring an equivalent runtime fake
  is required before the funnel-happy spec can run end-to-end). See the
  Post-merge Notes section below.

## Manual Stripe test-mode runbook

This runbook validates the real Stripe path against `stripe listen`. Run
locally before merge.

### Prerequisites

- Stripe CLI: `brew install stripe/stripe-cli/stripe`
- A Stripe account in test mode with a `pk_test_*` and `sk_test_*` key
- Backing services up: `docker compose up -d`
- Backend up with Stripe profile:
  ```bash
  STRIPE_SECRET_KEY=sk_test_xxx \
  STRIPE_WEBHOOK_SECRET=whsec_xxx \
  PAYMENTS_GATEWAY=stripe \
  SPRING_PROFILES_ACTIVE=local \
  ./gradlew :services:commerce-core:bootRun
  ```
- Frontend env (`apps/asoview-web/.env.local`):
  ```
  NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
  NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_test_xxx
  NEXT_PUBLIC_FAKE_CHECKOUT_MODE=0
  ```

### Steps

1. In one terminal, forward webhooks:
   ```bash
   stripe listen --forward-to localhost:8080/v1/payments/webhooks/stripe
   ```
   Copy the printed `whsec_*` into `STRIPE_WEBHOOK_SECRET` and restart
   the backend.
2. `cd apps/asoview-web && bun run dev` and open `http://localhost:3000`.
3. Sign in via the Auth Emulator (`docker compose up firebase-auth-emulator`)
   or real Google OAuth.
4. Browse to a product, pick a slot, click Book. You should land on
   `/checkout/{orderId}` with the Stripe Elements card form.
5. Card: `4242 4242 4242 4242`, any future expiry, any CVC, any postcode.
6. Click "支払う". The Stripe CLI should print a `payment_intent.succeeded`
   event being delivered to the backend.
7. The page should poll, transition to `PAID`, and route to
   `/tickets/{orderId}` with one or more QR codes rendered.
8. Verify in the Stripe Dashboard (Test mode) that the PaymentIntent
   shows as `succeeded`.

### Failure modes to verify

- Decline: card `4000 0000 0000 0002` → page surfaces "決済に失敗しました。"
  with a "もう一度試す" link.
- Cross-user: open `/checkout/{some-other-users-order-id}` in a separate
  signed-in browser → "この注文は見つかりませんでした。"
- Validity window: artificially shift the slot's `validFrom` to the
  future via SQL → `/tickets/{orderId}` shows "から利用可能" instead of
  the QR.

## Post-merge notes

1. **Promote `FakePaymentGateway` to `main` scope** (currently in
   `services/commerce-core/src/test/...`) so the CI compose stack can
   run the funnel-happy Playwright spec end-to-end with
   `payments.gateway=fake` + `?fakeMode=1`. The frontend harness already
   supports this; only the backend wiring is missing.
2. **Add `firebase-auth-emulator` to `docker-compose.yml`** so the
   `asoview-web-e2e` CI job can mint test users via the Auth Emulator
   REST signupNewUser endpoint. The `NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL`
   env is already wired through.
3. **Server-side pagination on `/v1/me/orders`** — small follow-up to
   convert from `List<OrderResponse>` to `Page<OrderResponse>`. The
   frontend page already accepts `?page=` and will swap transparently.

## Test plan

- [x] `bun run lint` (Biome) — green
- [x] `bun run typecheck` (tsc) — green
- [x] `bun run test` (Vitest, 43 tests) — green
- [x] `bun run build` (Next 16) — green
- [ ] `bun run test:e2e` (Playwright) — runs in CI via `asoview-web-e2e` job
- [ ] Manual Stripe test-mode runbook above
