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
  EOT
}

variable "google_oauth_client_secret_id" {
  type        = string
  description = <<-EOT
    Secret Manager resource ID of the Google OAuth client secret (format:
    projects/<number>/secrets/<name>). Terraform reads the latest secret
    version at apply time via google_secret_manager_secret_version. Store
    the client_secret itself out-of-band via `gcloud secrets versions add`.
  EOT
}
