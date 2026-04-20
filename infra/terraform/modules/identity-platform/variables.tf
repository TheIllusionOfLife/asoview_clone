variable "project_id" {
  type = string
}

variable "support_email" {
  type        = string
  description = "OAuth consent screen support email"
}

variable "google_oauth_client_id" {
  type        = string
  description = <<-EOT
    OAuth 2.0 Web Client ID for Google sign-in (format:
    NUMBER-hash.apps.googleusercontent.com). Provisioned manually in GCP
    Console (APIs & Services → Credentials → OAuth client ID) because the
    consent screen requires interactive publishing steps Terraform cannot
    automate. See docs/operations/google-oauth-setup.md.

    The OAuth client_secret is intentionally managed outside Terraform and
    set via GCP Console post-apply; no secret variable exists.
  EOT
}
