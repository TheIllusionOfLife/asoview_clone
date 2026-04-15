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

# ticketing-service runs under a dedicated GSA that only gets
# roles/spanner.databaseUser (no Cloud SQL, no broader project access).
# Spanner-level fine-grained enforcement is layered on via the
# `ticketing_service` database role defined in
# services/commerce-core/src/main/resources/db/spanner/V10__spanner_fine_grained_roles.sql
# plus roles/spanner.fineGrainedAccessUser on the database with a
# condition matching that role. Adding the fineGrainedAccessUser
# binding is deferred until the application code invokes
# setDatabaseRole("ticketing_service") on the DatabaseClient — otherwise
# the role grants are inert but still serve as defense-in-depth ready
# to enforce once wired.

resource "google_service_account" "ticketing_service" {
  account_id   = "ticketing-service"
  display_name = "ticketing-service workload identity"
  project      = var.project_id
}

resource "google_project_iam_member" "ticketing_service_spanner" {
  project = var.project_id
  role    = "roles/spanner.databaseUser"
  member  = "serviceAccount:${google_service_account.ticketing_service.email}"
}

resource "google_service_account_iam_member" "ticketing_service_workload_identity" {
  service_account_id = google_service_account.ticketing_service.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[core-services/ticketing-service]"
}
