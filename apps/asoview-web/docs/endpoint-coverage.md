# Endpoint ↔ Screen Coverage Matrix

Tracks every `/v1/**` endpoint shipped by `commerce-core` (PRs #17–#21) and
the asoview-web screen (or "funnel-internal" caller) that consumes it.
Phase 3e exit criterion #1: every row has either a UI entry point or an
explicit "funnel-internal" justification.

Last verified: 2026-04-08, Phase 3e (branch `feat/phase3e-web-features-polish`).

| Method | Path | Screen / caller | E2E spec |
|---|---|---|---|
| GET | `/v1/categories` | funnel-internal: `Facets.tsx` category dropdown (hardcoded until a category feed endpoint lands) | — |
| GET | `/v1/areas` | `/[locale]` landing, `/[locale]/areas/[area]` | `e2e/csr/funnel-cross-user.spec.ts` |
| GET | `/v1/products` | `/[locale]` landing (SimilarProducts strip), `/[locale]/areas/[area]` list | `e2e/csr/search.spec.ts`, `funnel-cross-user.spec.ts` |
| GET | `/v1/products/{id}` | `/[locale]/products/[id]` (product detail + i18n `?lang=`) | `funnel-cross-user.spec.ts` |
| GET | `/v1/products/{id}/availability` | `/[locale]/products/[id]` SlotPicker (called from CSR) | `funnel-cross-user.spec.ts` |
| GET | `/v1/products/{productId}/reviews` | `/[locale]/products/[id]` ReviewList | `e2e/csr/reviews.spec.ts` |
| POST | `/v1/reviews` | `/[locale]/products/[id]` ReviewForm (signed-in only) | `e2e/csr/reviews.spec.ts` |
| POST | `/v1/reviews/{reviewId}/helpful` | ReviewList HelpfulButton | `e2e/csr/reviews.spec.ts` |
| PATCH | `/v1/reviews/{reviewId}` | funnel-internal: not yet exposed — moderator/author edit flow is Phase 3f+ | — |
| DELETE | `/v1/reviews/{reviewId}` | funnel-internal: not yet exposed — moderator/author delete flow is Phase 3f+ | — |
| GET | `/v1/search` | `/[locale]/search` SearchResults | `e2e/csr/search.spec.ts` |
| GET | `/v1/search/suggest` | SearchBox autosuggest | `e2e/csr/search.spec.ts` |
| POST | `/v1/orders` | `/[locale]/cart` checkout submit (includes `pointsToUse`) | `funnel-happy` series + `idempotency.spec.ts` |
| GET | `/v1/orders/{orderId}` | `/[locale]/checkout/[orderId]` CheckoutClient, `/[locale]/tickets/[orderId]` | `CheckoutClient.token-refresh.test.tsx` (Vitest) |
| POST | `/v1/orders/{orderId}/payments` | CheckoutClient intent creation | `e2e/csr/funnel-cross-user.spec.ts` |
| POST | `/v1/payments/webhooks/stripe` | funnel-internal: Stripe → gateway, no UI | — |
| POST | `/v1/payments/webhooks/paypay` | funnel-internal: PayPay → gateway, no UI | — |
| GET | `/v1/me` | `Header.tsx` user pill, `useAuth` hook | `funnel-cross-user.spec.ts` |
| GET | `/v1/me/orders` | `/[locale]/me/orders` MyOrdersClient | `funnel-cross-user.spec.ts` |
| GET | `/v1/me/tickets` | `/[locale]/tickets/[orderId]` TicketsClient (filter `?orderId=`) | `e2e/csr/wallet.spec.ts`, `validity.spec.ts` |
| GET | `/v1/me/tickets/{ticketId}/apple-pass` | `AppleWalletButton` on ticket detail (validity-gated) | `e2e/csr/wallet.spec.ts` |
| GET | `/v1/me/tickets/{ticketId}/google-pass-link` | `GoogleWalletButton` on ticket detail (validity-gated) | `e2e/csr/wallet.spec.ts` |
| GET | `/v1/me/favorites` | `/[locale]/me/favorites` FavoritesClient | `e2e/csr/favorites.spec.ts` |
| PUT | `/v1/me/favorites/{productId}` | `FavoriteToggle` on ProductCard + product page | `e2e/csr/favorites.spec.ts` |
| DELETE | `/v1/me/favorites/{productId}` | `FavoriteToggle` off-toggle | `e2e/csr/favorites.spec.ts` |
| GET | `/v1/me/points` | `PointsBalance` header widget, `/[locale]/me/points` page, `/[locale]/cart` points-to-use clamp | `e2e/csr/points.spec.ts` |
| GET | `/healthz` | funnel-internal: load-balancer probe, no UI | — |

## Gaps to resolve in Phase 3f or a follow-up

- **Point ledger endpoint does not exist.** Backend ships only `GET /v1/me/points` returning `{balance}`. `/[locale]/me/points` renders a "履歴は近日公開予定" placeholder. Ledger endpoint + UI is deferred until backend exposes `GET /v1/me/points/ledger`.
- **Review edit / delete endpoints (PATCH + DELETE `/v1/reviews/{id}`) are un-surfaced.** The backend supports author edits and moderator deletes but there is no UI. Defer to a moderator/me-page Phase 3f.
- **`/v1/categories` is used only as a hardcoded dropdown.** When a real category listing endpoint is wired on the web, Facets should fetch it. Tracked as a 3e follow-up.
- **ReviewForm is on the product page, not the PAID order detail.** `OrderResponse.items` doesn't expose `productId` (only `productVariantId` + `slotId`), so there's no way to derive the product from a PAID order client-side. A future endpoint that hydrates items with `productId` would let us move the form under `/me/orders/[id]`.
