#!/usr/bin/env bash
# Grant a Firebase user the operator custom claims so they can hit
# GET /v1/op/** on reservation-service / urakata-reservation-web. Required
# claims: admin=true (ROLE_ADMIN on the gateway) + tenantId (scopes the
# operator to one tenant's products/slots/reservations).
#
# MERGE, don't overwrite: Firebase's accounts:update replaces customAttributes
# wholesale, so we read the existing claims first and merge the new fields in.
# This lets e2e-test-2 hold both SCANNER (via provision-scanner-claim.sh) and
# operator claims simultaneously.
#
# Required env:
#   OPERATOR_USER_EMAIL  email of the Firebase user to promote
#   OPERATOR_TENANT_ID   UUID of the tenant the operator manages
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
require OPERATOR_USER_EMAIL
require OPERATOR_TENANT_ID

TOKEN="$(gcloud auth print-access-token)"
API_KEY="$(gcloud secrets versions access latest \
  --project="$FIREBASE_PROJECT_ID" \
  --secret=firebase-web-config-apikey 2>/dev/null || echo "")"

if [[ -z "$API_KEY" ]]; then
  echo "ERROR: couldn't read firebase-web-config-apikey from Secret Manager." >&2
  echo "       Run scripts/bootstrap-dev-secrets.sh first." >&2
  exit 1
fi

lookup_response="$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:lookup?key=${API_KEY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"email\":[\"${OPERATOR_USER_EMAIL}\"]}")"

uid="$(echo "$lookup_response" | jq -r '.users[0].localId // empty')"
if [[ -z "$uid" ]]; then
  echo "ERROR: user ${OPERATOR_USER_EMAIL} not found" >&2
  exit 1
fi
echo "Resolved ${OPERATOR_USER_EMAIL} -> uid ${uid}"

# Identity Toolkit returns customAttributes as a JSON-encoded STRING (not an
# object) inside the users[0] payload. Empty / unset is treated as "{}".
existing_claims_str="$(echo "$lookup_response" | jq -r '.users[0].customAttributes // "{}"')"
if [[ -z "$existing_claims_str" || "$existing_claims_str" == "null" ]]; then
  existing_claims_str="{}"
fi
echo "Existing claims: ${existing_claims_str}"

# Merge existing claims with the new operator fields. jq --argjson reads a
# JSON value (vs --arg which quotes as string); we pass the parsed existing
# claims object that way so the merge operator (*) works.
merged_claims="$(jq -nc \
  --argjson existing "$existing_claims_str" \
  --arg tenantId "$OPERATOR_TENANT_ID" \
  '$existing * {admin: true, tenantId: $tenantId}')"

echo "Merged claims:  ${merged_claims}"

update_response="$(curl -sS -X POST \
  "https://identitytoolkit.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/accounts:update?key=${API_KEY}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"localId\":\"${uid}\",\"customAttributes\":$(echo "$merged_claims" | jq -Rs .)}")"

if echo "$update_response" | jq -e '.error' >/dev/null 2>&1; then
  err_msg="$(echo "$update_response" | jq -r '.error.message // "unknown error"')"
  echo "ERROR: Identity Toolkit accounts:update failed: ${err_msg}" >&2
  echo "       full response: ${update_response}" >&2
  exit 1
fi

echo "Granted ${OPERATOR_USER_EMAIL} claims: ${merged_claims}"
echo "User must sign out and back in on the operator UI to receive the updated token."
