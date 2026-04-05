variable "project_id" { type = string }
variable "region" { type = string }

resource "google_storage_bucket" "assets" {
  name          = "${var.project_id}-assets"
  location      = var.region
  project       = var.project_id
  force_destroy = true

  uniform_bucket_level_access = true
}

output "assets_bucket" {
  value = google_storage_bucket.assets.name
}
