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

  schema = jsonencode([
    { name = "event_id", type = "STRING", mode = "REQUIRED" },
    { name = "event_type", type = "STRING", mode = "REQUIRED" },
    { name = "order_id", type = "STRING", mode = "REQUIRED" },
    { name = "user_id", type = "STRING", mode = "NULLABLE" },
    { name = "status", type = "STRING", mode = "REQUIRED" },
    { name = "total_amount_jpy", type = "INTEGER", mode = "NULLABLE" },
    { name = "currency", type = "STRING", mode = "NULLABLE" },
    { name = "occurred_at", type = "TIMESTAMP", mode = "REQUIRED" },
    { name = "producer", type = "STRING", mode = "NULLABLE" },
  ])
}

resource "google_bigquery_table" "payment_events" {
  dataset_id          = google_bigquery_dataset.datasets["analytics_raw"].dataset_id
  table_id            = "payment_events"
  project             = var.project_id
  deletion_protection = false

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
