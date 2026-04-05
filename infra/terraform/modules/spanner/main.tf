variable "project_id" {
  type = string
}
variable "region" {
  type = string
}
variable "instance_name" {
  type    = string
  default = "asoview-clone"
}

resource "google_spanner_instance" "main" {
  name             = var.instance_name
  config           = "regional-${var.region}"
  display_name     = "Asoview Clone"
  project          = var.project_id
  processing_units = 100
}

resource "google_spanner_database" "main" {
  instance = google_spanner_instance.main.name
  name     = "asoview"
  project  = var.project_id
}

output "instance_name" {
  value = google_spanner_instance.main.name
}
