#!/usr/bin/env bash
# Bootstrap the asoview_clone dev cluster's secrets via Google Secret Manager.
#
# Reads credentials from the current shell environment and writes them to
# Secret Manager in the configured GCP project. External Secrets Operator,
# installed into the cluster via Argo CD, then syncs those GSM values into the
# k8s Secrets that each service's deployment.yaml references.
#
# Idempotent: every `gcloud secrets versions add` creates a new version, so
# rotations just mean re-running this script with new env values. If a secret
# doesn't exist yet, creates it first.
#
# Run once per environment after the user has:
#   1. Registered the DuckDNS subdomains (asoview-tickets, asoview-operator).
#   2. Generated a Gemini API key at ai.google.dev.
#   3. Rotated Stripe test keys (Dashboard → Developers → API keys).
#   4. (dev-only) No SendGrid needed — cluster uses MailHog as the SMTP sink.
#
# Required env vars:
#   FIREBASE_API_KEY, FIREBASE_APP_ID, FIREBASE_AUTH_DOMAIN, FIREBASE_PROJECT_ID
#   GOOGLE_API_KEY                (Gemini)
#   STRIPE_PUBLISHABLE_KEY, STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET
#
# Optional:
#   PROJECT_ID     GCP project (default: asoview-clone-dev)
#   SKIP_VALIDATION=1   skip the final ExternalSecret sync-wait

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-asoview-clone-dev}"

require() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: env var $name is required" >&2
    echo "       See scripts/README.md for where each value comes from." >&2
    exit 1
  fi
}

require FIREBASE_API_KEY
require FIREBASE_APP_ID
require FIREBASE_AUTH_DOMAIN
require FIREBASE_PROJECT_ID
require GOOGLE_API_KEY
require STRIPE_PUBLISHABLE_KEY
require STRIPE_SECRET_KEY
require STRIPE_WEBHOOK_SECRET

echo "==> Writing secrets to Secret Manager in project ${PROJECT_ID}"

upsert_secret() {
  local name="$1"
  local value="$2"
  if ! gcloud secrets describe "$name" --project="$PROJECT_ID" >/dev/null 2>&1; then
    echo "  creating $name"
    gcloud secrets create "$name" \
      --project="$PROJECT_ID" \
      --replication-policy=automatic >/dev/null
  fi
  printf '%s' "$value" | gcloud secrets versions add "$name" \
    --project="$PROJECT_ID" \
    --data-file=- >/dev/null
  echo "  upserted $name"
}

upsert_secret firebase-web-config-apikey      "$FIREBASE_API_KEY"
upsert_secret firebase-web-config-appid       "$FIREBASE_APP_ID"
upsert_secret firebase-web-config-authdomain  "$FIREBASE_AUTH_DOMAIN"
upsert_secret firebase-web-config-projectid   "$FIREBASE_PROJECT_ID"
upsert_secret gemini-api-key                  "$GOOGLE_API_KEY"
upsert_secret stripe-publishable-key          "$STRIPE_PUBLISHABLE_KEY"
upsert_secret stripe-secret-key               "$STRIPE_SECRET_KEY"
upsert_secret stripe-webhook-secret           "$STRIPE_WEBHOOK_SECRET"

# MailHog is SMTP-auth-less in dev. We write the SMTP target coordinates anyway
# so the reservation-service ExternalSecret has something to project.
upsert_secret smtp-host     "mailhog.core-services.svc.cluster.local"
upsert_secret smtp-port     "1025"
upsert_secret smtp-username ""
upsert_secret smtp-password ""
upsert_secret smtp-from     "noreply@asoview-clone.dev"

if [[ "${SKIP_VALIDATION:-0}" == "1" ]]; then
  echo "==> Skipping ExternalSecret sync-wait (SKIP_VALIDATION=1)"
  exit 0
fi

echo "==> Validating ExternalSecret sync"
if ! kubectl get crd externalsecrets.external-secrets.io >/dev/null 2>&1; then
  echo "  External Secrets Operator CRDs not found — skipping validation."
  echo "  Install ESO first (Argo CD Application \"external-secrets\" must be Synced)."
  exit 0
fi

# Wait up to 3 minutes for every ExternalSecret resource in the cluster to
# report SecretSynced=True. If any fail, surface the details.
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  pending="$(kubectl get externalsecrets -A -o json \
    | jq -r '.items[] | select(.status.conditions // [] | map(select(.type=="Ready" and .status=="True")) | length == 0) | "\(.metadata.namespace)/\(.metadata.name)"')"
  if [[ -z "$pending" ]]; then
    echo "  All ExternalSecrets are Synced."
    exit 0
  fi
  echo "  Waiting on: $(echo "$pending" | tr '\n' ' ')"
  sleep 10
done

echo "ERROR: Some ExternalSecrets never became Ready within 3 minutes." >&2
kubectl get externalsecrets -A >&2
exit 1
