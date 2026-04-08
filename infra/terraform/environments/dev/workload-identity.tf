# Per-environment Google Service Accounts that the in-cluster workloads
# bind to via Workload Identity. Mirrors the pattern in
# infra/terraform/modules/opensearch/main.tf:88-92 — the
# google_service_account_iam_member resource grants the in-cluster KSA
# permission to mint OAuth tokens as the GSA.
#
# The KSA name + namespace MUST match the k8s manifest under
# infra/k8s/<service>/base/serviceaccount.yaml. The GSA email is exposed
# as an output so the dev kustomize overlay can patch the
# `iam.gke.io/gcp-service-account` annotation with the real value
# instead of baking the dev-specific email into the base manifest.

resource "google_service_account" "commerce_core" {
  account_id   = "commerce-core"
  display_name = "commerce-core workload identity"
  project      = var.project_id
}

resource "google_project_iam_member" "commerce_core_cloudsql" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.commerce_core.email}"
}

resource "google_project_iam_member" "commerce_core_spanner" {
  project = var.project_id
  role    = "roles/spanner.databaseUser"
  member  = "serviceAccount:${google_service_account.commerce_core.email}"
}

resource "google_service_account_iam_member" "commerce_core_workload_identity" {
  service_account_id = google_service_account.commerce_core.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[core-services/commerce-core]"
}
