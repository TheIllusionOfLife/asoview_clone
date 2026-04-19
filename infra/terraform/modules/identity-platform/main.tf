// Identity Platform (Firebase-compatible) configuration for Asoview consumer auth.
//
// The asoview-web frontend (PR 3d) signs users in with Google OAuth via the
// Firebase JS SDK. Spring Cloud Gateway and commerce-core verify the ID
// tokens via the Firebase Admin SDK. This module enables Identity Platform
// on the project and registers the Google provider.
//
// The OAuth client_secret is intentionally NOT managed by Terraform. Reading
// it into a resource attribute materializes the plaintext secret into
// Terraform state on every plan/apply — and the state backend today is
// local/unencrypted. The resource is created with a dummy value and
// lifecycle.ignore_changes keeps Terraform from overwriting whatever the
// operator sets via Console. First-time bring-up: `terraform apply` creates
// the enabled provider with the dummy, then the operator pastes the real
// secret via GCP Console → Identity Platform → Google provider → Edit.
// See docs/operations/google-oauth-setup.md.

resource "google_project_service" "identity_toolkit" {
  project            = var.project_id
  service            = "identitytoolkit.googleapis.com"
  disable_on_destroy = false
}

resource "google_identity_platform_config" "default" {
  project                    = var.project_id
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
  client_id     = var.google_oauth_client_id
  client_secret = "MANAGED_OUTSIDE_TERRAFORM"

  lifecycle {
    // Client secret is set in GCP Console post-apply and never owned by
    // Terraform. Plans never propose updates; state holds the literal dummy.
    ignore_changes = [client_secret]
  }

  depends_on = [google_identity_platform_config.default]
}

output "identity_platform_enabled" {
  value = true
}
