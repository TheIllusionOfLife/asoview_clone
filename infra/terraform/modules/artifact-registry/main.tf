variable "project_id" { type = string }
variable "region" { type = string }

resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "asoview-clone"
  format        = "DOCKER"
  project       = var.project_id
}

output "repository_url" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.docker.repository_id}"
}
