variable "project_id" {
  type        = string
  description = "GCP project id for the dev environment"
  default     = "asoview-clone-dev"
}

variable "region" {
  type    = string
  default = "asia-northeast1"
}

variable "zone" {
  type    = string
  default = "asia-northeast1-a"
}

variable "support_email" {
  type        = string
  description = "OAuth consent screen support email"
  default     = "ops@asoview-clone.invalid"
}

variable "domain" {
  type        = string
  description = "Public apex domain for the dev environment (only if you own one). Leave empty to use DuckDNS instead."
  default     = ""
}

variable "duckdns_subdomain" {
  type        = string
  description = "DuckDNS subdomain (without the .duckdns.org suffix). NOTE: this variable is informational only — it gates the Cloud DNS resources in edge.tf and is reflected in the web_url output, but the actual hostname in infra/k8s/edge/ingress.yaml is hardcoded. If you change this, you MUST also sed-replace asoview-clone-dev.duckdns.org in ingress.yaml (host + TLS hosts)."
  default     = "asoview-clone-dev"
}

