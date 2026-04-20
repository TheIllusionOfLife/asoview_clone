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

variable "billing_account_id" {
  type        = string
  description = "GCP billing account ID (format: XXXXXX-XXXXXX-XXXXXX) used for Cloud Billing budgets. Required; supply via terraform.tfvars or TF_VAR_billing_account_id."

  validation {
    condition     = can(regex("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$", var.billing_account_id))
    error_message = "billing_account_id must be in the form XXXXXX-XXXXXX-XXXXXX (uppercase alphanumerics)."
  }
}

variable "notification_email" {
  type        = string
  description = "Email address receiving Cloud Billing budget + monitoring alerts for the dev environment. Required; supply via terraform.tfvars or TF_VAR_notification_email."

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.notification_email))
    error_message = "notification_email must be a well-formed email address."
  }
}

variable "google_oauth_client_id" {
  type        = string
  description = "OAuth 2.0 Web Client ID for Google sign-in. Provisioned manually in GCP Console; see docs/operations/google-oauth-setup.md. The matching client_secret is set in GCP Console post-apply and is intentionally NOT managed by Terraform (avoids plaintext in state)."
}

