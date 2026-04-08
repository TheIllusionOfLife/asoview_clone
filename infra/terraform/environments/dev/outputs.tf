output "web_url" {
  # Precedence: a custom domain (if set) wins over the DuckDNS fallback,
  # so operators who explicitly configure `domain` get an output that
  # matches what they serve. DuckDNS is the default when `domain` is empty.
  value = (
    var.domain != "" ? "https://${var.domain}" :
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
