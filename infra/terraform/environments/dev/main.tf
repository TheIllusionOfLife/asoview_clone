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
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
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

module "opensearch" {
  source       = "../../modules/opensearch"
  project_id   = var.project_id
  region       = var.region
  cluster_name = "asoview-clone-dev"
}

module "wallet_kms" {
  source     = "../../modules/wallet-kms"
  project_id = var.project_id
  region     = var.region
}

module "identity_platform" {
  source        = "../../modules/identity-platform"
  project_id    = var.project_id
  support_email = var.support_email
}

# Variables, outputs, and the edge (static IP + managed cert prerequisites)
# live in sibling files: variables.tf, outputs.tf, edge.tf.
