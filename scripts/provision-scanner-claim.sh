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
#
# Identity Toolkit admin endpoints authenticate via user OAuth (gcloud
# access token). Do NOT pass ?key=<API_KEY>: when the API key and the user
# OAuth token are both bound to the same Firebase project, Google's Identity
# Toolkit check "API Key and the authentication credential are from different
# projects" fires anyway. Instead, set x-goog-user-project so ADC/user creds
# have a quota project, which is the supported auth shape for these admin
# calls.
TOKEN="$(gcloud auth print-access-token)"

uid_response="$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:lookup" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "x-goog-user-project: ${FIREBASE_PROJECT_ID}" \
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
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:update" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "x-goog-user-project: ${FIREBASE_PROJECT_ID}" \
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
