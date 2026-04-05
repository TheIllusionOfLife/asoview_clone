variable "project_id" {
  type = string
}
variable "region" {
  type = string
}
variable "network_id" {
  type = string
}

resource "google_redis_instance" "main" {
  name               = "asoview-clone-redis"
  tier               = "BASIC"
  memory_size_gb     = 1
  region             = var.region
  project            = var.project_id
  authorized_network = var.network_id
}

output "host" {
  value = google_redis_instance.main.host
}
