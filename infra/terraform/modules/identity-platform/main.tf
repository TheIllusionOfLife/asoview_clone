// Identity Platform (Firebase-compatible) configuration for Asoview consumer auth.
//
// The asoview-web frontend (PR 3d) signs users in with Google OAuth via the
// Firebase JS SDK. Spring Cloud Gateway and commerce-core verify the ID
// tokens via the Firebase Admin SDK. This module enables Identity Platform
// on the project and wires the Google OAuth client created out-of-band in
// GCP Console (see docs/operations/google-oauth-setup.md).

data "google_project" "current" {
  project_id = var.project_id
}

data "google_secret_manager_secret_version" "google_oauth_secret" {
  # secret is the fully-qualified resource ID (projects/<num>/secrets/<name>);
  # the provider parses the project from it, so passing project here too
  # would conflict when project_id is the human slug vs. the number.
  secret  = var.google_oauth_client_secret_id
  version = "latest"
}

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
  client_secret = data.google_secret_manager_secret_version.google_oauth_secret.secret_data

  depends_on = [
    google_identity_platform_config.default,
    google_secret_manager_secret_iam_member.identity_platform_access,
  ]
}

// Grant the Identity Platform service agent read access on the OAuth client
// secret. Without this, Identity Platform cannot rotate / refresh the secret
// at sign-in time once Terraform manages the resource.
resource "google_secret_manager_secret_iam_member" "identity_platform_access" {
  project   = var.project_id
  secret_id = var.google_oauth_client_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:service-${data.google_project.current.number}@gcp-sa-identitytoolkit.iam.gserviceaccount.com"
}

output "identity_platform_enabled" {
  value = true
}
