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
  description = "DuckDNS subdomain (without the .duckdns.org suffix). The full hostname becomes <this>.duckdns.org. Used in the ingress.yaml host + TLS cert."
  default     = "asoview-clone-dev"
}

