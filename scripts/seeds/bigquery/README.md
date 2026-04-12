# BigQuery Seed Data

Seed scripts for populating BigQuery with realistic analytics data for development and testing.

## Prerequisites

- `gcloud` CLI authenticated with access to `asoview-clone-dev` project
- BigQuery tables created via Terraform (`infra/terraform/modules/bigquery/`)

## Usage

Run scripts in order:

```bash
# 1. Seed product-venue mapping (reference table)
bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/004_seed_product_venue_mapping.sql

# 2. Seed order events (68 rows: 60 paid + 8 cancelled)
bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/001_seed_order_events.sql

# 3. Seed payment events (60 rows matching paid orders)
bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/002_seed_payment_events.sql

# 4. Create mart views
bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/003_create_mart_views.sql
```

## Data Profile

- 10 demo users (matching `R__seed_catalog.sql`)
- 50 products (matching `R__seed_catalog.sql` UUIDs)
- 8 venues (Tokyo, Yokohama, Kyoto, Osaka, Sapporo, Fukuoka, Okinawa, Nagoya)
- Orders spread across 90 days for partition testing
- Popular products (1, 5, 8) have repeat purchases for ranking differentiation
- Providers alternate between `stripe` and `paypay`

## Idempotency

All scripts use `MERGE ... WHEN NOT MATCHED` so they can be re-run safely.
