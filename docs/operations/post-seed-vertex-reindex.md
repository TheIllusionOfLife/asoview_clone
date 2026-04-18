# Post-Seed Vertex AI Search Reindex

When the commerce-core repeatable seed migration (`R__seed_catalog.sql`) changes — for example a title / description / translation rewrite — Postgres rows get upserted on the next pod restart, but Vertex AI Search is **not** refreshed automatically.

## Why

`DevSearchReindexer` publishes a `ProductUpsertedEvent` for every seeded product on startup, but `ProductIndexEventListener` is a log-only `AFTER_COMMIT` hook today — it does not forward to search-service. The search-service `IndexerBackfillJob` also runs on pod startup but short-circuits when the `asoview-backfill-marker-v1` sentinel doc is present, so a restart alone does not re-ingest.

A proper cross-service wire (Pub/Sub `product-upserted` topic → search-service subscriber → DocumentService upsert) is tracked as the "replace log-only listener" TODO in `ProductIndexEventListener.java`. Until that ships, a seed content rewrite requires one of the operational steps below.

## Option A — force a full reindex (preferred for wholesale changes)

1. Delete the backfill marker:
   ```sh
   gcloud alpha discovery-engine documents delete asoview-backfill-marker-v1 \
     --data-store=asoview-products --location=global \
     --project=asoview-clone-dev
   ```
2. Roll search-service:
   ```sh
   kubectl -n search rollout restart deployment/search-service
   ```
3. On startup, `IndexerBackfillJob` paginates `/v1/products?status=ACTIVE` from commerce-core, upserts every doc into Vertex, and writes a fresh marker.
4. Verify via `curl https://asoview-clone-dev.duckdns.org/api/v1/search?q=<new-title-term>`.

## Option B — per-product reindex (for targeted changes)

If only a handful of products changed, hit the existing admin endpoint once per product:

```sh
kubectl -n search port-forward svc/search-service 8082:8082 &
for id in $(cut -d, -f1 <product-ids.csv); do
  curl -X POST "http://localhost:8082/v1/search/admin/reindex/${id}"
done
```

Endpoint: `IndexerController#reindex` (`services/search-service/src/main/java/com/asoviewclone/searchservice/indexer/IndexerController.java`).

## Follow-up (not in scope of this runbook)

Replace the log-only listener with a Pub/Sub publish so updates propagate automatically. Tracked in the `ProductIndexEventListener.java` javadoc TODO.
