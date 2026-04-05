variable "project_id" { type = string }
variable "region" { type = string }

resource "google_compute_network" "main" {
  name                    = "asoview-clone-vpc"
  auto_create_subnetworks = false
  project                 = var.project_id
}

resource "google_compute_subnetwork" "main" {
  name          = "asoview-clone-subnet"
  ip_cidr_range = "10.0.0.0/20"
  region        = var.region
  network       = google_compute_network.main.id
  project       = var.project_id

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.4.0.0/14"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.8.0.0/20"
  }
}

output "network_id" {
  value = google_compute_network.main.id
}

output "subnetwork_id" {
  value = google_compute_subnetwork.main.id
}
