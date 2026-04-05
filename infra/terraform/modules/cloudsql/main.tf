variable "project_id" {
  type = string
}
variable "region" {
  type = string
}
variable "instance_name" {
  type    = string
  default = "asoview-clone-pg"
}
variable "network_id" {
  type = string
}

resource "google_sql_database_instance" "main" {
  name             = var.instance_name
  database_version = "POSTGRES_16"
  region           = var.region
  project          = var.project_id

  settings {
    tier = "db-f1-micro"

    ip_configuration {
      ipv4_enabled    = false
      private_network = var.network_id
    }
  }

  deletion_protection = false
}

resource "google_sql_database" "main" {
  name     = "asoview"
  instance = google_sql_database_instance.main.name
  project  = var.project_id
}

output "connection_name" {
  value = google_sql_database_instance.main.connection_name
}
