variable "project_id" { type = string }
variable "region" { type = string }

resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "asoview-clone"
  format        = "DOCKER"
  project       = var.project_id

  # Storage grows ~0.1 GB per CI push. Without cleanup the repo reached
  # 38 GB across 593 digests before these policies were introduced. Each
  # policy is independent; Artifact Registry applies ALL matching policies
  # with KEEP winning over DELETE for the same version.
  cleanup_policies {
    id     = "keep-minimum-versions-per-image"
    action = "KEEP"
    most_recent_versions {
      keep_count = 5
    }
  }

  cleanup_policies {
    id     = "delete-untagged-after-7-days"
    action = "DELETE"
    condition {
      tag_state  = "UNTAGGED"
      older_than = "604800s" # 7 days
    }
  }

  cleanup_policies {
    id     = "delete-old-tagged-after-30-days"
    action = "DELETE"
    condition {
      tag_state  = "TAGGED"
      older_than = "2592000s" # 30 days
    }
  }
}

output "repository_url" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.docker.repository_id}"
}
