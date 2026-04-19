# BigQuery mart bootstrap

`analytics_mart` views power `PopularityScoreSyncJob` (search relevance boost) and the
analytics dashboard. Terraform at `infra/terraform/modules/bigquery/main.tf` owns the
four views (`daily_bookings`, `product_ranking`, `venue_performance`, `consumer_funnel`);
the SQL at `scripts/seeds/bigquery/003_create_mart_views.sql` is the recovery path if
Terraform state is ever reset.

## Normal bring-up (new environment)

```sh
cd infra/terraform/environments/<env>
terraform apply -target=module.bigquery
```

Four `google_bigquery_table` adds. Views reference `${var.project_id}.analytics_raw.*`
so the module is env-neutral.

## Populate raw events (dev only)

Real traffic populates `analytics_raw.order_events` via `analytics-ingest` from the
`order-events` Pub/Sub subscription. For a fresh dev environment with no traffic yet:

```sh
bq query --use_legacy_sql=false --project_id=<PROJECT_ID> \
  < scripts/seeds/bigquery/001_seed_order_events.sql
```

Idempotent MERGE: 60 rows keyed on `event_id`; safe to re-run. Seed user_ids and
product_ids match the Postgres seed catalog.

## Verify

```sh
bq query --use_legacy_sql=false --project_id=<PROJECT_ID> \
  'SELECT COUNT(*) FROM `<PROJECT_ID>.analytics_mart.product_ranking`'
```

Expect 50 rows when the seed is applied (one per seeded product). On real-traffic
environments the count grows with distinct `product_id` values in paid orders.

## Sync job

`PopularityScoreSyncJob` in search-service polls `product_ranking` hourly
(`search.popularity-sync.interval-ms` = 3,600,000). Successful run logs:

```
PopularityScoreSyncJob: sync complete: N succeeded, 0 failed
```

where N is the row count. `0 succeeded, 0 failed` means the view exists but is empty
(no order events yet) — harmless, but no popularity boost until events flow.

## Recovery if Terraform state is lost

Run the seed SQL directly; `CREATE OR REPLACE VIEW` is idempotent:

```sh
bq query --use_legacy_sql=false --project_id=<PROJECT_ID> \
  < scripts/seeds/bigquery/003_create_mart_views.sql
```

Then reimport the resources into Terraform state on the next apply, or let the next
apply recreate them (`CREATE OR REPLACE VIEW` semantics are compatible with Terraform
recreation).
