// Identity Platform (Firebase-compatible) configuration for Asoview consumer auth.
//
// The asoview-web frontend (PR 3d) signs users in with Google OAuth via the
// Firebase JS SDK. Spring Cloud Gateway and commerce-core verify the ID
// tokens via the Firebase Admin SDK. This module enables Identity Platform
// on the project and creates the OAuth client used by the web app.
//
// The actual OAuth client secret is fetched out-of-band from the GCP
// console and stored in Secret Manager — Terraform cannot create OAuth
// brand consent screens without manual approval, so this module just
// declares the dependency.

variable "project_id" {
  type = string
}

variable "support_email" {
  type        = string
  description = "OAuth consent screen support email"
}

resource "google_project_service" "identity_toolkit" {
  project            = var.project_id
  service            = "identitytoolkit.googleapis.com"
  disable_on_destroy = false
}

resource "google_identity_platform_config" "default" {
  project  = var.project_id
  autodelete_anonymous_users = false

  sign_in {
    allow_duplicate_emails = false

    email {
      enabled           = true
      password_required = false
    }
  }

  depends_on = [google_project_service.identity_toolkit]
}

resource "google_identity_platform_default_supported_idp_config" "google" {
  project       = var.project_id
  enabled       = true
  idp_id        = "google.com"
  client_id     = "PLACEHOLDER_OAUTH_CLIENT_ID"
  client_secret = "PLACEHOLDER_OAUTH_CLIENT_SECRET"

  depends_on = [google_identity_platform_config.default]

  lifecycle {
    // The real client_id/client_secret are managed by hand in the GCP
    // console (OAuth consent screens cannot be Terraform-managed end to
    // end). Ignore drift on these so plan stays clean.
    ignore_changes = [client_id, client_secret]
  }
}

output "identity_platform_enabled" {
  value = true
}
