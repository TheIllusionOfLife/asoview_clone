# Nightly shutdown of the GKE default node pool.
#
# Active hours: 08:00-24:00 JST (16 hours/day). Outside those, the pool
# is scaled to 0, which zeroes out the node VM + PD-balanced disk cost
# for ~8 hours every night. At e2-standard-2 x 2 list price in Tokyo
# that saves roughly $1.00/day ($30/month).
#
# Implementation: a dedicated GSA + two Cloud Scheduler jobs that POST
# to the Container API's nodePools.setSize endpoint with an OAuth token
# minted from this GSA. We deliberately avoid Cluster Autoscaler here —
# running an autoscaler alongside the scheduler would race, reverting
# the scale-to-0 as soon as a pending pod (from a Deployment ReplicaSet)
# tripped a scale-up.
#
# The scheduler can be disabled temporarily by pausing both jobs in the
# Cloud Scheduler console, or by running:
#   gcloud scheduler jobs pause gke-nightly-{stop,start} \
#     --location=<region> --project=<project>

# Cloud Scheduler API must be enabled on the project before any job can
# be created. App Engine is already enabled (it's a hard dependency of
# Cloud Scheduler and was enabled when the project was first provisioned),
# so no additional App Engine app resource is needed here.
resource "google_project_service" "cloudscheduler" {
  project                    = var.project_id
  service                    = "cloudscheduler.googleapis.com"
  disable_on_destroy         = false
  disable_dependent_services = false
}

# Service account the scheduler impersonates to call the Container API.
resource "google_service_account" "gke_scheduler" {
  project      = var.project_id
  account_id   = "gke-nightly-scheduler"
  display_name = "GKE nightly node-pool resize scheduler"
  description  = "Used by Cloud Scheduler to resize the default node pool at 00:00/08:00 JST."
}

# container.developer is the minimum role that can call
# projects.locations.clusters.nodePools.setSize. It does NOT grant
# access to workload data (no pod exec, no logs read).
resource "google_project_iam_member" "gke_scheduler_container_developer" {
  project = var.project_id
  role    = "roles/container.developer"
  member  = "serviceAccount:${google_service_account.gke_scheduler.email}"
}

# Cloud Scheduler's own service agent needs actAs on the target SA so it
# can mint OAuth tokens when firing the job. Without this binding the
# scheduler job fails with PERMISSION_DENIED at invocation time.
data "google_project" "current" {
  project_id = var.project_id
}

resource "google_service_account_iam_member" "scheduler_acts_as_gke_scheduler" {
  service_account_id = google_service_account.gke_scheduler.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-cloudscheduler.iam.gserviceaccount.com"
}

# Shared URL — same node pool, different body payload per job.
locals {
  node_pool_set_size_url = format(
    "https://container.googleapis.com/v1/projects/%s/locations/%s/clusters/%s/nodePools/%s:setSize",
    var.project_id,
    module.gke.cluster_location,
    module.gke.cluster_name,
    module.gke.default_node_pool_name,
  )
}

resource "google_cloud_scheduler_job" "gke_nightly_stop" {
  project          = var.project_id
  region           = var.region
  name             = "gke-nightly-stop"
  description      = "Scale default-pool to 0 nodes at 00:00 JST."
  schedule         = "0 0 * * *"
  time_zone        = "Asia/Tokyo"
  attempt_deadline = "180s"

  retry_config {
    retry_count = 2
  }

  http_target {
    http_method = "POST"
    uri         = local.node_pool_set_size_url
    headers = {
      "Content-Type" = "application/json"
    }
    body = base64encode(jsonencode({ nodeCount = 0 }))

    oauth_token {
      service_account_email = google_service_account.gke_scheduler.email
      # Default scope https://www.googleapis.com/auth/cloud-platform is
      # implicit; no need to set it.
    }
  }

  depends_on = [
    google_project_service.cloudscheduler,
    google_project_iam_member.gke_scheduler_container_developer,
    google_service_account_iam_member.scheduler_acts_as_gke_scheduler,
  ]
}

resource "google_cloud_scheduler_job" "gke_nightly_start" {
  project          = var.project_id
  region           = var.region
  name             = "gke-nightly-start"
  description      = "Scale default-pool back to 2 nodes at 08:00 JST."
  schedule         = "0 8 * * *"
  time_zone        = "Asia/Tokyo"
  attempt_deadline = "180s"

  retry_config {
    retry_count = 2
  }

  http_target {
    http_method = "POST"
    uri         = local.node_pool_set_size_url
    headers = {
      "Content-Type" = "application/json"
    }
    body = base64encode(jsonencode({ nodeCount = 2 }))

    oauth_token {
      service_account_email = google_service_account.gke_scheduler.email
    }
  }

  depends_on = [
    google_project_service.cloudscheduler,
    google_project_iam_member.gke_scheduler_container_developer,
    google_service_account_iam_member.scheduler_acts_as_gke_scheduler,
  ]
}
