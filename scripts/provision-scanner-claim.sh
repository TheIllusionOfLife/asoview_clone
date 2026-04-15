#!/usr/bin/env bash
# Grant a Firebase user the SCANNER custom claim so they can hit
# POST /v1/op/tickets/redeem. Used by the E2E walkthrough and by the initial
# operator onboarding.
#
# Required env:
#   SCANNER_USER_EMAIL   email of the Firebase user to promote
#   SCANNER_VENUE_IDS    comma-separated list of venue UUIDs the user can scan
# Optional:
#   FIREBASE_PROJECT_ID  default: asoview-clone-dev

set -euo pipefail

FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID:-asoview-clone-dev}"

require() {
  if [[ -z "${!1:-}" ]]; then
    echo "ERROR: $1 is required" >&2
    exit 1
  fi
}
require SCANNER_USER_EMAIL
require SCANNER_VENUE_IDS

# Resolve email -> uid via the REST lookup endpoint (avoids Admin SDK
# dependency for a one-off script).
TOKEN="$(gcloud auth print-access-token)"
API_KEY="$(gcloud secrets versions access latest \
  --project="$FIREBASE_PROJECT_ID" \
  --secret=firebase-web-config-apikey 2>/dev/null || echo "")"

if [[ -z "$API_KEY" ]]; then
  echo "ERROR: couldn't read firebase-web-config-apikey from Secret Manager." >&2
  echo "       Run scripts/bootstrap-dev-secrets.sh first." >&2
  exit 1
fi

uid_response="$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:lookup?key=${API_KEY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"email\":[\"${SCANNER_USER_EMAIL}\"]}")"

uid="$(echo "$uid_response" | jq -r '.users[0].localId // empty')"
if [[ -z "$uid" ]]; then
  echo "ERROR: user ${SCANNER_USER_EMAIL} not found" >&2
  exit 1
fi
echo "Resolved ${SCANNER_USER_EMAIL} -> uid ${uid}"

# Build the customAttributes JSON the Identity Toolkit v1 update endpoint expects.
venues_json="$(echo "$SCANNER_VENUE_IDS" | jq -Rc 'split(",")')"
claims="$(jq -nc \
  --arg roles "SCANNER" \
  --argjson venues "$venues_json" \
  '{roles: [$roles], scannerVenues: $venues}')"

update_response="$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:update?key=${API_KEY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"localId\":\"${uid}\",\"customAttributes\":$(echo "$claims" | jq -Rs .)}")"

if echo "$update_response" | jq -e '.error' >/dev/null 2>&1; then
  err_msg="$(echo "$update_response" | jq -r '.error.message // "unknown error"')"
  echo "ERROR: Identity Toolkit accounts:update failed: ${err_msg}" >&2
  echo "       full response: ${update_response}" >&2
  exit 1
fi

echo "Granted ${SCANNER_USER_EMAIL} claims: ${claims}"
echo "User must sign out and back in to receive the updated token."
