# Edge ingress prerequisites: a reserved REGIONAL static IP that the
# ingress-nginx Service (type: LoadBalancer, in the `edge` namespace)
# pins via `spec.loadBalancerIP` in its Helm values. The user does a
# two-phase bootstrap: terraform apply → read the IP from
# `terraform output static_ip` → sed-replace the `EDIT_ME_STATIC_IP`
# placeholder in `infra/argocd/applications/ingress-nginx.yaml` →
# commit + push. Argo CD then syncs ingress-nginx with the pinned IP.
#
# Why regional (not global): ingress-nginx runs as a k8s Service
# type=LoadBalancer, which provisions a GCP regional L4 TCP forwarding
# rule. Global L7 addresses are for GCE Ingress, which we've removed.
# The two address resources are not interchangeable.
#
# TLS is handled in-cluster by cert-manager + Let's Encrypt via HTTP-01,
# not by Google ManagedCertificate. See infra/k8s/edge/clusterissuer.yaml
# and the ingress-nginx Argo CD Application.

resource "google_compute_address" "edge" {
  name         = "asoview-clone-dev-edge"
  project      = var.project_id
  region       = var.region
  address_type = "EXTERNAL"
  description  = "Reserved regional static IP for the ingress-nginx Service in the edge namespace"
}

resource "google_dns_managed_zone" "edge" {
  count       = var.domain != "" ? 1 : 0
  name        = "asoview-clone-dev"
  dns_name    = "${var.domain}."
  project     = var.project_id
  description = "DNS zone for the dev environment apex domain"
}

# A record pointing the apex at the static IP. Only created when the user
# has provided their own domain AND opted into Cloud DNS hosting. If the
# user prefers to keep DNS at their registrar, leave var.domain empty and
# create the A record manually pointing at the `static_ip` output.
resource "google_dns_record_set" "edge_a" {
  count        = var.domain != "" ? 1 : 0
  name         = "${var.domain}."
  type         = "A"
  ttl          = 300
  managed_zone = google_dns_managed_zone.edge[0].name
  project      = var.project_id
  rrdatas      = [google_compute_address.edge.address]
}
