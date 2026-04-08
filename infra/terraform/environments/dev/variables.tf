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
  description = "Public apex domain for the dev environment (e.g. dev.example.com). Leave empty to skip ManagedCertificate + Cloud DNS zone."
  default     = ""
}
