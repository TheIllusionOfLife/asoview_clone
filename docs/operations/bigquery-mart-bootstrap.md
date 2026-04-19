# BigQuery mart bootstrap

`analytics_mart` views power `PopularityScoreSyncJob` (search relevance boost) and the
analytics dashboard. Terraform at `infra/terraform/modules/bigquery/main.tf` owns the
four views (`daily_bookings`, `product_ranking`, `venue_performance`, `consumer_funnel`);
the SQL at `scripts/seeds/bigquery/003_create_mart_views.sql` is the recovery path if
Terraform state is ever reset.

All shell snippets below assume the working directory is the repo root. Use
`$(git rev-parse --show-toplevel)` to prefix paths if you're running them from
elsewhere (e.g. from inside `infra/terraform/environments/<env>` during a
`terraform apply` session).

## Normal bring-up (new environment)

```sh
cd "$(git rev-parse --show-toplevel)/infra/terraform/environments/<env>"
terraform apply -target=module.bigquery
```

Four `google_bigquery_table` adds. Views reference `${var.project_id}.analytics_raw.*`
so the module is env-neutral.

## Populate raw events (dev only)

Real traffic populates `analytics_raw.order_events` via `analytics-ingest` from the
`order-events` Pub/Sub subscription. For a fresh dev environment with no traffic yet:

```sh
bq query --use_legacy_sql=false --project_id=<PROJECT_ID> \
  < "$(git rev-parse --show-toplevel)/scripts/seeds/bigquery/001_seed_order_events.sql"
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

The seed SQL is the fastest path to restore the views as live BigQuery resources,
but `CREATE OR REPLACE VIEW` does NOT reconcile with Terraform state — a subsequent
`terraform apply` will fail with `googleapi: Error 409: Already Exists` because the
views exist in BigQuery but not in state.

Pick one recovery mode:

**Mode A — recreate from Terraform (preferred if BigQuery views can be dropped).**

```sh
bq rm -f -t <PROJECT_ID>:analytics_mart.daily_bookings
bq rm -f -t <PROJECT_ID>:analytics_mart.product_ranking
bq rm -f -t <PROJECT_ID>:analytics_mart.venue_performance
bq rm -f -t <PROJECT_ID>:analytics_mart.consumer_funnel
cd "$(git rev-parse --show-toplevel)/infra/terraform/environments/<env>"
terraform apply -target=module.bigquery
```

**Mode B — adopt the live views into fresh state (preferred if views must stay up).**

Run the seed SQL first so the views exist, then `terraform import` each into state:

```sh
bq query --use_legacy_sql=false --project_id=<PROJECT_ID> \
  < "$(git rev-parse --show-toplevel)/scripts/seeds/bigquery/003_create_mart_views.sql"

cd "$(git rev-parse --show-toplevel)/infra/terraform/environments/<env>"
terraform import module.bigquery.google_bigquery_table.mart_daily_bookings \
  projects/<PROJECT_ID>/datasets/analytics_mart/tables/daily_bookings
terraform import module.bigquery.google_bigquery_table.mart_product_ranking \
  projects/<PROJECT_ID>/datasets/analytics_mart/tables/product_ranking
terraform import module.bigquery.google_bigquery_table.mart_venue_performance \
  projects/<PROJECT_ID>/datasets/analytics_mart/tables/venue_performance
terraform import module.bigquery.google_bigquery_table.mart_consumer_funnel \
  projects/<PROJECT_ID>/datasets/analytics_mart/tables/consumer_funnel
terraform plan
```

Expect the `plan` after import to show no changes (or a drift list if the seed SQL
has strayed from the module). Reconcile by editing whichever side is wrong.
