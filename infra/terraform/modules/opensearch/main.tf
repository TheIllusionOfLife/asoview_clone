// OpenSearch on GKE for the search-service.
//
// Asoview! product search lives in services/search-service (Spring Boot 4)
// and queries OpenSearch with the analysis-kuromoji plugin for Japanese
// tokenization. We do NOT use a managed service: GCP has no first-party
// OpenSearch and the marketplace alternatives are expensive for the
// volume Phase 2 needs. A 3-node statefulset on a dedicated node pool is
// the cheapest production-grade choice.
//
// This module provisions:
// - A namespace `search`
// - Persistent disks for each replica via PVCs (provisioned by GKE)
// - A Workload Identity-bound GCP service account for snapshot uploads
// - A KMS key ring for at-rest data encryption
//
// The actual statefulset + service + index template are owned by the
// Argo CD application manifests under infra/k8s/search/. This module
// only provides the GCP-side primitives so the cluster has the IAM and
// KMS prerequisites in place when Argo CD reconciles.

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "cluster_name" {
  type        = string
  description = "GKE cluster the search workload runs on (for KSA binding)"
}

variable "namespace" {
  type    = string
  default = "search"
}

resource "google_kms_key_ring" "search" {
  name     = "asoview-search"
  location = var.region
  project  = var.project_id
}

resource "google_kms_crypto_key" "opensearch_data" {
  name            = "opensearch-data"
  key_ring        = google_kms_key_ring.search.id
  rotation_period = "7776000s" // 90 days

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_service_account" "opensearch_snapshots" {
  account_id   = "opensearch-snapshots"
  display_name = "OpenSearch snapshot uploader"
  project      = var.project_id
}

resource "google_storage_bucket" "opensearch_snapshots" {
  name                        = "${var.project_id}-opensearch-snapshots"
  location                    = var.region
  uniform_bucket_level_access = true
  project                     = var.project_id

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      age = 30
    }
    action {
      type          = "SetStorageClass"
      storage_class = "NEARLINE"
    }
  }
}

resource "google_storage_bucket_iam_member" "opensearch_snapshots_writer" {
  bucket = google_storage_bucket.opensearch_snapshots.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.opensearch_snapshots.email}"
}

resource "google_service_account_iam_member" "opensearch_workload_identity" {
  service_account_id = google_service_account.opensearch_snapshots.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.namespace}/opensearch]"
}

output "kms_key_id" {
  value = google_kms_crypto_key.opensearch_data.id
}

output "snapshot_bucket" {
  value = google_storage_bucket.opensearch_snapshots.name
}

output "snapshot_sa_email" {
  value = google_service_account.opensearch_snapshots.email
}
