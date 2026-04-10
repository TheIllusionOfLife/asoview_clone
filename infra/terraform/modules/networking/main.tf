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

# Private Services Access (VPC peering) for Cloud SQL + Memorystore
# private-IP instances. Without this, google_sql_database_instance with
# ip_configuration.private_network fails with "the network doesn't have
# at least 1 private services connection".
resource "google_compute_global_address" "private_services" {
  name          = "asoview-clone-psa"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.main.id
  project       = var.project_id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.main.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]
}

output "network_id" {
  value = google_compute_network.main.id
}

output "subnetwork_id" {
  value = google_compute_subnetwork.main.id
}

output "private_services_connection" {
  value       = google_service_networking_connection.private_services.id
  description = "Handle that downstream modules (cloudsql, redis) depend_on so they wait for the peering."
}
