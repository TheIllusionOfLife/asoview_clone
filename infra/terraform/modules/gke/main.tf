variable "project_id" {
  type = string
}
variable "region" {
  type = string
}
variable "zone" {
  type = string
}
variable "cluster_name" {
  type    = string
  default = "asoview-clone"
}
variable "network_id" {
  type = string
}
variable "subnetwork_id" {
  type = string
}

resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  location = var.zone
  project  = var.project_id

  network    = var.network_id
  subnetwork = var.subnetwork_id

  initial_node_count       = 1
  remove_default_node_pool = true

  release_channel {
    channel = "REGULAR"
  }

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }
}

resource "google_container_node_pool" "default" {
  name     = "default-pool"
  location = var.zone
  cluster  = google_container_cluster.primary.name
  project  = var.project_id

  # Utilization on e2-standard-4 x 2 was CPU 5-6%, memory 42% (~11 GB of
  # 32 GB). Drop to e2-standard-2 (2 vCPU / 8 GB) x 2 = 16 GB total,
  # leaving ~30% memory headroom for Java startup transients.
  #
  # No autoscaling: the nightly scheduler (see cost-schedules.tf in the
  # dev environment) explicitly resizes the pool via setSize. Running an
  # autoscaler in parallel would race — the scheduler's scale-to-0 would
  # be reverted by the autoscaler as soon as a pending pod triggered it.
  # A static count is the simpler mental model for dev.
  #
  # node_count (not initial_node_count) is the correct field for a
  # statically sized pool without autoscaling; initial_node_count is
  # semantically the creation-time value and is primarily used with
  # autoscaling blocks.
  node_count = 2

  lifecycle {
    # The scheduler resizes the pool via setSize from outside Terraform.
    # Without this ignore, every `terraform apply` would undo the
    # scheduler's off-hours resize. initial_node_count is also listed
    # so terraform doesn't force-replace on provider-default drift.
    ignore_changes = [node_count, initial_node_count]
  }

  node_config {
    machine_type = "e2-standard-2"
    disk_size_gb = 50
    oauth_scopes = [
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
      "https://www.googleapis.com/auth/devstorage.read_only",
    ]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
}

output "cluster_endpoint" {
  value = google_container_cluster.primary.endpoint
}

output "cluster_name" {
  value = google_container_cluster.primary.name
}

output "cluster_location" {
  value = google_container_cluster.primary.location
}

output "default_node_pool_name" {
  value = google_container_node_pool.default.name
}
