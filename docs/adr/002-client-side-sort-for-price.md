# ADR 002: Client-side minPrice sort

**Status**: Accepted
**Date**: 2026-04-20

## Context

`GET /v1/search?sort=price_asc` (and `price_desc`) must return products
monotonically ordered by `minPrice`. The site has offered the control since
#22 but the server never sorted.

Discovery Engine generic vertical rejects every `orderBy` form on our
custom `minPrice` field:

- PR #71 added `keyPropertyMapping: price` to the schema and emitted
  `orderBy=price asc`. The schema update crash-looped search-service on
  startup (PR #72 revert). `price` annotation is not supported on the
  generic vertical.
- `orderBy=minPrice asc` and `orderBy=structData.minPrice asc` both return
  `INVALID_ARGUMENT: Unsupported field in orderBy`.
- The `isInvalidOrderBy` fallback (#71) keeps the worst case at HTTP 200
  with relevance order, but the user-visible outcome is "sort ignored".

The natural next step was migrating the data store to Discovery Engine's
Retail vertical, which natively supports sort on `price`. A Codex review
(2026-04-19) flagged this as an architectural mismatch:

- Retail vertical expects a fixed commerce product schema — `priceInfo.price`
  (nested), `categories[]` (array), `title`, `uri` — not our 9 flat custom
  fields.
- Our `minPrice` does not map 1-for-1 to `priceInfo.price`; `categoryId`
  (scalar) does not map to `categories[]`; `areaId`, `status`, and
  `popularityScore` would have to move into Retail's `attributes{}` custom
  map.
- The ingestion API path differs. A toy probe with 2 sample docs would not
  surface real-ingest failures at the 50-product scale.
- Live rollback is cold: the Pub/Sub subscription feeds whichever store
  search-service writes to. Warm rollback would require dual-write.

## Decision

Sort client-side in `VertexAiSearchQueryService`:

1. When `sort ∈ {"price_asc", "price_desc"}`, skip Discovery Engine
   `orderBy` entirely.
2. Fetch a wide window (`CLIENT_SORT_WINDOW = 100` — also Discovery
   Engine's `pageSize` cap) without orderBy.
3. Sort the hits by `minPrice` in Java. `null` prices always trail
   regardless of direction.
4. Apply the caller's original `(page, size)` to the sorted list and
   return.

`ProductSearchResponse.totalElements` is capped at `CLIENT_SORT_WINDOW` on
the price-sort path. Discovery Engine's `totalSize` can legitimately exceed
the window (up to all filter matches), but returning the raw total would
let callers compute phantom pages past `hits.size()` — empty content with
a page index that looks valid. The cap keeps `totalPages = ceil(total /
size)` honest at the cost of hiding the "there's actually more" signal on
the price-sort path only.

Retail-vertical migration stays on the long-term roadmap but is not
scheduled.

## Consequences

**Pros**

- Zero infrastructure change; no Terraform, no schema, no data-store
  recreation.
- No possibility of a PR #72-style crash-loop.
- Sort now actually sorts — the audit assertion tightens from "HTTP 200
  acceptable" to "monotonic non-decreasing across hits" on
  `sort=price_asc`.
- Popularity boost (`popularityBoostSpec()`) still runs on the wide-window
  fetch; the sort just happens after.

**Cons**

- Globally monotonic only up to 100 filter matches. If the caller filters
  down to ≤ 100 products (realistic for a category + area + price-range
  page), sort is exact. If the filter returns > 100 matches, the 101st
  product and beyond are silently excluded from the sort window. A warn
  log fires so we notice before users do.
- Current seed catalog is 50 products; match-all of the full catalog still
  fits in one window. Revisit when the catalog crosses ~500 products or
  when product filters routinely return > 100 matches.
- Two Discovery Engine round-trips for a first-page sort query vs. one for
  relevance: the window fetch plus... actually, still one. Sort is Java-
  only after the single fetch. Cost is 100-doc page size instead of 20,
  which Discovery Engine bills on documents returned × request (negligible
  at our scale).

## Revisit triggers

- Catalog passes ~500 products. `CLIENT_SORT_WINDOW` can go to Discovery
  Engine's hard cap of 100 pageSize + iterative pagination (expensive), or
  we bite the bullet on Retail vertical.
- A non-price sort requirement emerges (rating, name, popularity). Each
  would need its own client-side path or a different backing store.
- Discovery Engine adds `orderBy` support for arbitrary numeric fields on
  the generic vertical. Monitor release notes.

## References

- PR #71 / PR #72: `keyPropertyMapping` crash-loop story.
- PR #75: popularity-sync IAM + BQ mart view provisioning.
- Codex review conversation: 2026-04-19, weighed Retail-vertical
  migration against client-side sort and enterprise-tier probe.
- `services/search-service/src/main/java/com/asoviewclone/searchservice/query/service/VertexAiSearchQueryService.java`
  — implementation.
- `apps/asoview-web/e2e/smoke/service-audit.spec.ts` —
  monotonicity assertions.
