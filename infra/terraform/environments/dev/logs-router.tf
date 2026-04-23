# Cloud Logging ingestion is one of the top cost drivers on this project
# (~$2-5/day). The single biggest source of volume is kube-system + the
# stack operational components that nobody on this project will ever
# read: GKE control plane agents, Argo CD reconciliation chatter,
# cert-manager challenges, external-secrets polling, Google Managed
# Prometheus metrics collector logs.
#
# This exclusion filter on the default Logging sink drops those logs
# before they're billed. The filter keeps ERROR+ entries from those
# namespaces so genuine breakage still surfaces, and leaves all other
# workload logs untouched.
#
# To disable the exclusion temporarily (e.g. to debug Argo sync failures):
#   gcloud logging sinks update _Default --project=<project> \
#     --clear-exclusions
# Then re-apply via `terraform apply`.

resource "google_logging_project_exclusion" "kube_infra_noise" {
  project     = var.project_id
  name        = "exclude-kube-infra-noise"
  description = "Drop INFO+ logs from control-plane / infra namespaces that we never read."

  # Only k8s_container and k8s_pod carry resource.labels.namespace_name.
  # k8s_node and k8s_cluster logs have no namespace label, so including
  # them in the type set would have produced dead branches (the
  # namespace_name AND term can never match those types).
  filter = <<-EOT
    resource.type=("k8s_container" OR "k8s_pod")
    resource.labels.namespace_name=("kube-system" OR "gmp-system" OR "argocd" OR "cert-manager" OR "external-secrets")
    severity<ERROR
  EOT
}
