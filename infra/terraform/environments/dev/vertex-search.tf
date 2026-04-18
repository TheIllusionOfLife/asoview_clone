# Vertex AI Search (Discovery Engine) data store + engine + Workload Identity
# wiring for the search-service pod. Replaces the self-hosted OpenSearch
# StatefulSet on GKE.
#
# Schema is applied by the pod itself at startup (VertexAiSearchSchemaBootstrap
# via SchemaServiceClient.updateSchema) rather than a terraform-managed
# google_discovery_engine_schema resource. This keeps the schema source of
# truth in services/search-service/src/main/resources/vertex/products-schema.json
# so app + infra don't drift.

resource "google_project_service" "discoveryengine" {
  project            = var.project_id
  service            = "discoveryengine.googleapis.com"
  disable_on_destroy = false
}

resource "google_discovery_engine_data_store" "products" {
  provider = google-beta

  location          = "global"
  data_store_id     = "asoview-products"
  display_name      = "Asoview Products"
  industry_vertical = "GENERIC"
  content_config    = "NO_CONTENT"
  solution_types    = ["SOLUTION_TYPE_SEARCH"]

  project = var.project_id

  depends_on = [google_project_service.discoveryengine]
}

resource "google_discovery_engine_search_engine" "products" {
  provider = google-beta

  engine_id      = "asoview-products-engine"
  collection_id  = "default_collection"
  location       = google_discovery_engine_data_store.products.location
  display_name   = "Asoview Products Search Engine"
  data_store_ids = [google_discovery_engine_data_store.products.data_store_id]

  search_engine_config {
    search_tier = "SEARCH_TIER_STANDARD"
    # search_add_ons omitted — LLM add-on costs extra and isn't needed for
    # MVP faceted / boosted search. Revisit once chatbot-driven retrieval
    # is validated against Vertex.
  }

  project = var.project_id
}

# search-service GSA. Single role (roles/discoveryengine.editor) covers both
# read (search queries) and write (document ingest via IndexerService).
# Splitting into viewer + editor would need separate pods; the modular monolith
# pattern here keeps them on the same workload.

resource "google_service_account" "search_service_vertex" {
  account_id   = "search-service-vertex"
  display_name = "search-service Vertex AI Search workload identity"
  project      = var.project_id
}

resource "google_project_iam_member" "search_service_vertex_admin" {
  project = var.project_id
  # `roles/discoveryengine.admin` (not `.editor`) because schema bootstrap
  # requires `discoveryengine.schemas.update` + `schemas.create`, which
  # are present in admin but not editor. Document write + search (indexer
  # + query paths) also fit under admin. This is a single-workload project
  # so the broader role is acceptable.
  role   = "roles/discoveryengine.admin"
  member = "serviceAccount:${google_service_account.search_service_vertex.email}"
}

resource "google_service_account_iam_member" "search_service_vertex_workload_identity" {
  service_account_id = google_service_account.search_service_vertex.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[search/search-service]"
}

# PopularityScoreSyncJob reads `analytics_mart.product_ranking` and writes
# popularityScore back into Vertex. Needs project-level `bigquery.jobUser`
# to run queries and dataset-level `bigquery.dataViewer` on analytics_mart
# to read the ranking table. Without these the job logs
# "Access Denied: Project ...: User does not have bigquery.jobs.create
# permission" on every schedule tick (the known-limitation tracked until
# this PR — see plan "Known limitations"). Narrower than a blanket
# project dataViewer so other datasets (ads_mart, ops_raw) stay off-limits.
resource "google_project_iam_member" "search_service_bigquery_job_user" {
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.search_service_vertex.email}"
}

resource "google_bigquery_dataset_iam_member" "search_service_analytics_mart_viewer" {
  dataset_id = "analytics_mart"
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${google_service_account.search_service_vertex.email}"
  project    = var.project_id
  # The dataset is provisioned by module.bigquery; reference it via depends_on
  # because dataset_id is a string literal (no implicit edge). Without this,
  # a fresh `terraform apply` can race the binding and fail with
  # "Dataset not found: analytics_mart".
  depends_on = [module.bigquery]
}
