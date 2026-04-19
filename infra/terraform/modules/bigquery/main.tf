variable "project_id" { type = string }
variable "region" { type = string }

locals {
  datasets = [
    "analytics_raw",
    "analytics_mart",
    "ops_raw",
    "ads_raw",
    "ads_mart",
  ]
}

resource "google_bigquery_dataset" "datasets" {
  for_each   = toset(local.datasets)
  dataset_id = each.value
  location   = var.region
  project    = var.project_id
}

# --- analytics_raw tables (populated by analytics-ingest from Pub/Sub) ---

resource "google_bigquery_table" "order_events" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_raw"].dataset_id
  table_id            = "order_events"
  project             = var.project_id
  deletion_protection = false

  time_partitioning {
    type  = "DAY"
    field = "occurred_at"
  }

  clustering = ["event_type"]

  schema = jsonencode([
    { name = "event_id", type = "STRING", mode = "REQUIRED" },
    { name = "event_type", type = "STRING", mode = "REQUIRED" },
    { name = "order_id", type = "STRING", mode = "REQUIRED" },
    { name = "user_id", type = "STRING", mode = "NULLABLE" },
    { name = "status", type = "STRING", mode = "REQUIRED" },
    { name = "subtotal_jpy", type = "INTEGER", mode = "NULLABLE" },
    { name = "currency", type = "STRING", mode = "NULLABLE" },
    { name = "occurred_at", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "producer", type = "STRING", mode = "NULLABLE" },
    { name = "product_id", type = "STRING", mode = "NULLABLE" },
  ])
}

resource "google_bigquery_table" "product_venue_mapping" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_raw"].dataset_id
  table_id            = "product_venue_mapping"
  project             = var.project_id
  deletion_protection = false

  schema = jsonencode([
    { name = "product_id", type = "STRING", mode = "REQUIRED" },
    { name = "venue_id", type = "STRING", mode = "REQUIRED" },
    { name = "venue_name", type = "STRING", mode = "REQUIRED" },
  ])
}

resource "google_bigquery_table" "payment_events" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_raw"].dataset_id
  table_id            = "payment_events"
  project             = var.project_id
  deletion_protection = false

  time_partitioning {
    type  = "DAY"
    field = "occurred_at"
  }

  clustering = ["event_type"]

  schema = jsonencode([
    { name = "event_id", type = "STRING", mode = "REQUIRED" },
    { name = "event_type", type = "STRING", mode = "REQUIRED" },
    { name = "payment_id", type = "STRING", mode = "NULLABLE" },
    { name = "order_id", type = "STRING", mode = "REQUIRED" },
    { name = "status", type = "STRING", mode = "REQUIRED" },
    { name = "provider", type = "STRING", mode = "NULLABLE" },
    { name = "amount_jpy", type = "INTEGER", mode = "NULLABLE" },
    { name = "currency", type = "STRING", mode = "NULLABLE" },
    { name = "occurred_at", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "producer", type = "STRING", mode = "NULLABLE" },
  ])
}

# --- analytics_mart views (derived from analytics_raw tables) ---
#
# These mirror scripts/seeds/bigquery/003_create_mart_views.sql. Declaring them
# here puts mart definitions under the same IaC gate as the raw tables so a
# `terraform plan` catches drift. The SQL seed file is now the recovery path
# if Terraform state is ever reset; it's kept in sync with the view definitions
# below.

resource "google_bigquery_table" "mart_daily_bookings" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_mart"].dataset_id
  table_id            = "daily_bookings"
  project             = var.project_id
  deletion_protection = false

  view {
    query          = <<-EOT
      SELECT
        DATE(occurred_at, 'Asia/Tokyo') AS booking_date,
        COUNT(DISTINCT order_id) AS order_count,
        SUM(subtotal_jpy) AS revenue_jpy,
        SAFE_DIVIDE(SUM(subtotal_jpy), COUNT(DISTINCT order_id)) AS avg_order_value_jpy
      FROM `${var.project_id}.analytics_raw.order_events`
      WHERE event_type = 'order.paid'
      GROUP BY 1
    EOT
    use_legacy_sql = false
  }

  depends_on = [google_bigquery_table.order_events]
}

resource "google_bigquery_table" "mart_product_ranking" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_mart"].dataset_id
  table_id            = "product_ranking"
  project             = var.project_id
  deletion_protection = false

  view {
    query          = <<-EOT
      SELECT
        product_id,
        COUNT(DISTINCT order_id) AS order_count,
        SUM(subtotal_jpy) AS total_revenue_jpy,
        RANK() OVER (ORDER BY COUNT(DISTINCT order_id) DESC) AS popularity_rank
      FROM `${var.project_id}.analytics_raw.order_events`
      WHERE event_type = 'order.paid' AND product_id IS NOT NULL
      GROUP BY 1
    EOT
    use_legacy_sql = false
  }

  depends_on = [google_bigquery_table.order_events]
}

resource "google_bigquery_table" "mart_venue_performance" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_mart"].dataset_id
  table_id            = "venue_performance"
  project             = var.project_id
  deletion_protection = false

  view {
    query          = <<-EOT
      SELECT
        m.venue_id,
        m.venue_name,
        COUNT(DISTINCT e.order_id) AS order_count,
        SUM(e.subtotal_jpy) AS total_revenue_jpy,
        SAFE_DIVIDE(SUM(e.subtotal_jpy), COUNT(DISTINCT e.order_id)) AS avg_order_value_jpy
      FROM `${var.project_id}.analytics_raw.order_events` e
      JOIN `${var.project_id}.analytics_raw.product_venue_mapping` m
        ON e.product_id = m.product_id
      WHERE e.event_type = 'order.paid'
      GROUP BY 1, 2
    EOT
    use_legacy_sql = false
  }

  depends_on = [
    google_bigquery_table.order_events,
    google_bigquery_table.product_venue_mapping,
  ]
}

resource "google_bigquery_table" "mart_consumer_funnel" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_mart"].dataset_id
  table_id            = "consumer_funnel"
  project             = var.project_id
  deletion_protection = false

  view {
    query          = <<-EOT
      SELECT
        user_id,
        MIN(DATE(occurred_at, 'Asia/Tokyo')) AS first_order_date,
        MAX(DATE(occurred_at, 'Asia/Tokyo')) AS last_order_date,
        COUNT(DISTINCT order_id) AS total_orders,
        SUM(subtotal_jpy) AS ltv_jpy,
        SAFE_DIVIDE(SUM(subtotal_jpy), COUNT(DISTINCT order_id)) AS avg_order_value_jpy
      FROM `${var.project_id}.analytics_raw.order_events`
      WHERE event_type = 'order.paid' AND user_id IS NOT NULL
      GROUP BY 1
    EOT
    use_legacy_sql = false
  }

  depends_on = [google_bigquery_table.order_events]
}
