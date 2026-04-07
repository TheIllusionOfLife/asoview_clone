// KMS keys for wallet pass signing material.
//
// Apple Wallet .pkpass passes are signed with a PKCS#7 detached signature
// over manifest.json. The signing cert + private key must be Apple-issued
// (chained to the Apple WWDR intermediate) and rotated when they expire.
//
// Google Wallet save URLs are JWTs signed with an RS256 service-account
// private key. The key is provisioned out-of-band by Google and stored
// here.
//
// Both secrets are encrypted at rest with a CMEK and the actual blobs
// live in Secret Manager. The commerce-core wallet module reads them
// at startup via the workload-identity service account.

variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

resource "google_kms_key_ring" "wallet" {
  name     = "asoview-wallet"
  location = var.region
  project  = var.project_id
}

resource "google_kms_crypto_key" "wallet_secrets" {
  name            = "wallet-secrets"
  key_ring        = google_kms_key_ring.wallet.id
  rotation_period = "7776000s" // 90 days

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_secret_manager_secret" "apple_wallet_p12" {
  secret_id = "apple-wallet-cert-p12"
  project   = var.project_id

  replication {
    user_managed {
      replicas {
        location = var.region
        customer_managed_encryption {
          kms_key_name = google_kms_crypto_key.wallet_secrets.id
        }
      }
    }
  }
}

resource "google_secret_manager_secret" "apple_wallet_password" {
  secret_id = "apple-wallet-cert-password"
  project   = var.project_id

  replication {
    user_managed {
      replicas {
        location = var.region
        customer_managed_encryption {
          kms_key_name = google_kms_crypto_key.wallet_secrets.id
        }
      }
    }
  }
}

resource "google_secret_manager_secret" "google_wallet_sa_key" {
  secret_id = "google-wallet-sa-key"
  project   = var.project_id

  replication {
    user_managed {
      replicas {
        location = var.region
        customer_managed_encryption {
          kms_key_name = google_kms_crypto_key.wallet_secrets.id
        }
      }
    }
  }
}

resource "google_service_account" "wallet" {
  account_id   = "asoview-wallet"
  display_name = "Asoview wallet pass signer"
  project      = var.project_id
}

resource "google_secret_manager_secret_iam_member" "apple_p12_accessor" {
  secret_id = google_secret_manager_secret.apple_wallet_p12.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.wallet.email}"
}

resource "google_secret_manager_secret_iam_member" "apple_password_accessor" {
  secret_id = google_secret_manager_secret.apple_wallet_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.wallet.email}"
}

resource "google_secret_manager_secret_iam_member" "google_sa_accessor" {
  secret_id = google_secret_manager_secret.google_wallet_sa_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.wallet.email}"
}

output "wallet_sa_email" {
  value = google_service_account.wallet.email
}

output "kms_key_id" {
  value = google_kms_crypto_key.wallet_secrets.id
}
