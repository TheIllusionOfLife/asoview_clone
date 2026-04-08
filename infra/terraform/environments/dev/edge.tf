# Edge ingress prerequisites: a reserved global static IP that the GKE
# Ingress (in the `edge` namespace) attaches to via the
# `kubernetes.io/ingress.global-static-ip-name` annotation, plus an
# optional Cloud DNS managed zone gated on var.domain.
#
# ManagedCertificate is created INSIDE the cluster (k8s manifest), not
# from terraform — that resource lives in `infra/k8s/edge/` so Argo CD
# manages it alongside the Ingress. Terraform's job ends at the static
# IP and (optionally) the DNS zone.

resource "google_compute_global_address" "edge" {
  name        = "asoview-clone-dev-edge"
  project     = var.project_id
  description = "Reserved global static IP for the dev cluster edge ingress"
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
  rrdatas      = [google_compute_global_address.edge.address]
}
