output "web_url" {
  # Precedence must match the edge.tf condition that actually provisions
  # Cloud DNS: the custom-domain URL is only reachable when duckdns is
  # disabled (otherwise no DNS zone/A record is created for it). When
  # duckdns_subdomain is set, report the DuckDNS URL regardless of
  # var.domain so the output matches what the cluster actually serves.
  value = (
    var.domain != "" && var.duckdns_subdomain == "" ? "https://${var.domain}" :
    var.duckdns_subdomain != "" ? "https://${var.duckdns_subdomain}.duckdns.org" :
    "http://${google_compute_address.edge.address}"
  )
  description = "Consumer-facing asoview-web URL (HTTPS once the Let's Encrypt cert is Ready)"
}

output "static_ip" {
  value       = google_compute_address.edge.address
  description = "Reserved regional static IP. Point the DuckDNS A record at this, then paste it into infra/argocd/applications/ingress-nginx.yaml (replacing EDIT_ME_STATIC_IP)."
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

output "commerce_core_gsa_email" {
  value       = google_service_account.commerce_core.email
  description = "GSA email to patch into infra/k8s/commerce-core/overlays/dev kustomize annotation."
}
