terraform {
  required_version = ">= 1.5"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.0"
    }
  }
}

provider "google" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
  billing_project       = var.project_id
}

provider "google-beta" {
  project               = var.project_id
  region                = var.region
  user_project_override = true
  billing_project       = var.project_id
}

module "networking" {
  source     = "../../modules/networking"
  project_id = var.project_id
  region     = var.region
}

module "gke" {
  source        = "../../modules/gke"
  project_id    = var.project_id
  region        = var.region
  zone          = var.zone
  cluster_name  = "asoview-clone-dev"
  network_id    = module.networking.network_id
  subnetwork_id = module.networking.subnetwork_id
}

module "spanner" {
  source        = "../../modules/spanner"
  project_id    = var.project_id
  region        = var.region
  instance_name = "asoview-clone-dev"
}

module "cloudsql" {
  source        = "../../modules/cloudsql"
  project_id    = var.project_id
  region        = var.region
  instance_name = "asoview-clone-dev-pg"
  network_id    = module.networking.network_id
  depends_on    = [module.networking]
}

module "redis" {
  source     = "../../modules/redis"
  project_id = var.project_id
  region     = var.region
  network_id = module.networking.network_id
  depends_on = [module.networking]
}

module "pubsub" {
  source     = "../../modules/pubsub"
  project_id = var.project_id

  # Grant search-service-vertex GSA `roles/pubsub.subscriber` on the
  # product-index-events subscription so the auto-reindex pipeline works.
  # GSA is declared in vertex-search.tf; pass its email through so the
  # pubsub module can bind IAM without needing a back-reference.
  search_service_member = "serviceAccount:${google_service_account.search_service_vertex.email}"
}

module "bigquery" {
  source     = "../../modules/bigquery"
  project_id = var.project_id
  region     = var.region
}

module "storage" {
  source     = "../../modules/storage"
  project_id = var.project_id
  region     = var.region
}

module "artifact_registry" {
  source     = "../../modules/artifact-registry"
  project_id = var.project_id
  region     = var.region
}

# The `module "opensearch"` (KMS key, snapshot GCS bucket, GSA
# opensearch-snapshots, Workload Identity binding) was removed when the
# search-service migrated to Vertex AI Search (Discovery Engine). See
# infra/terraform/environments/dev/vertex-search.tf.

module "wallet_kms" {
  source     = "../../modules/wallet-kms"
  project_id = var.project_id
  region     = var.region
}

module "identity_platform" {
  source                 = "../../modules/identity-platform"
  project_id             = var.project_id
  support_email          = var.support_email
  google_oauth_client_id = var.google_oauth_client_id
}

# Variables, outputs, and the edge (static IP + managed cert prerequisites)
# live in sibling files: variables.tf, outputs.tf, edge.tf.
