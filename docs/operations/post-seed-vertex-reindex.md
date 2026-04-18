# Post-Seed Vertex AI Search Reindex

## Normal path (automatic)

Seed updates now propagate to Vertex AI Search without operator action:

1. `R__seed_catalog.sql` checksum changes → Flyway re-runs the repeatable migration on the next commerce-core pod start (or on demand via `mvn flyway:repair`-equivalent).
2. `DevSearchReindexer` publishes a `ProductUpsertedEvent` per product during the seed re-run.
3. `ProductIndexEventListener` (AFTER_COMMIT) forwards the productId to the `product-index-events` Pub/Sub topic.
4. search-service's `ProductUpsertedSubscriber` pulls the message, calls `VertexAiSearchIndexerService.reindex(productId)`, upserts into Discovery Engine.
5. New titles / translations are searchable within a few seconds of deploy — no `gcloud` or `kubectl` steps required.

Verify via:

```sh
NEW_TITLE_TERM="pottery studio"  # replace with the term you seeded
curl "https://asoview-clone-dev.duckdns.org/api/v1/search?q=${NEW_TITLE_TERM// /%20}"
```

The URL is quoted so shell metacharacters (`?`, `&`) inside the query string don't trip word-splitting; URL-encode spaces yourself if the seed title contains any.

## Troubleshooting

If the Pub/Sub path is degraded (topic / subscription mis-provisioned, search-service GSA missing subscriber role, commerce-core can't publish), use one of the escape hatches below. The startup `IndexerBackfillJob` in search-service still acts as a floor — worst case, a pod roll re-seeds Vertex from commerce-core's REST endpoint.

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
4. Verify via `curl "https://asoview-clone-dev.duckdns.org/api/v1/search?q=NEW_TITLE_TERM"` (replace the placeholder with your seeded term).

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
