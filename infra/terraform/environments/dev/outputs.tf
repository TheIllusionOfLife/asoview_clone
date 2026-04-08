output "web_url" {
  value       = var.domain != "" ? "https://${var.domain}" : "http://${google_compute_global_address.edge.address}"
  description = "Consumer-facing asoview-web URL (HTTPS once ManagedCertificate is ACTIVE)"
}

output "static_ip" {
  value       = google_compute_global_address.edge.address
  description = "Reserved global static IP. Point the domain's A record at this."
}

output "gateway_internal_url" {
  value = "http://gateway.edge.svc.cluster.local"
}

output "cloudsql_connection_name" {
  value = module.cloudsql.connection_name
}

output "spanner_instance_id" {
  value = module.spanner.instance_name
}

output "artifact_registry_repo" {
  value = module.artifact_registry.repository_url
}
