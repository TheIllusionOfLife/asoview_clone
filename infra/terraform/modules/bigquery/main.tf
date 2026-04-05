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
