#!/usr/bin/env bash
# Full ship-readiness check. Exercises the complete consumer → pass → QR →
# scan → operator-approval + email loop against the live dev cluster and
# exits 0 on success. This is the merge gate for PR 5d.
#
# Required env vars:
#   E2E_TEST_EMAIL              seed consumer email (signed-up in Firebase)
#   E2E_TEST_PASSWORD           seed consumer password
#   E2E_SCANNER_EMAIL           seed scanner-operator email
#   E2E_SCANNER_PASSWORD        seed scanner-operator password
#   E2E_FIREBASE_API_KEY        Firebase Web API key (for REST sign-in)
#   SCANNER_VENUE_IDS           comma-separated venue UUIDs for the scanner
#   SEEDED_PRODUCT_VARIANT_ID   a variant to buy (from the seed dataset)
#
# Optional:
#   API_BASE_URL                default: https://asoview-clone-dev.duckdns.org/api
#   MAILHOG_PORT_FORWARD        default: 18025 (local port for MailHog HTTP)

set -euo pipefail

API_BASE_URL="${API_BASE_URL:-https://asoview-clone-dev.duckdns.org/api}"
MAILHOG_PORT_FORWARD="${MAILHOG_PORT_FORWARD:-18025}"

require() {
  if [[ -z "${!1:-}" ]]; then
    echo "ERROR: $1 is required" >&2
    exit 1
  fi
}
require E2E_TEST_EMAIL
require E2E_TEST_PASSWORD
require E2E_SCANNER_EMAIL
require E2E_SCANNER_PASSWORD
require E2E_FIREBASE_API_KEY
require SCANNER_VENUE_IDS
require SEEDED_PRODUCT_VARIANT_ID

pass_count=0
fail_count=0
step() {
  local name="$1"
  shift
  # Avoid ((counter++)) — under `set -e`, the post-increment expression returns
  # the pre-value, and a value of 0 counts as a non-zero exit status, aborting
  # the script. $((counter + 1)) form always evaluates to non-zero.
  if "$@"; then
    echo "  PASS  $name"
    pass_count=$((pass_count + 1))
  else
    echo "  FAIL  $name" >&2
    fail_count=$((fail_count + 1))
  fi
}

firebase_signin() {
  curl -sS -X POST \
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${E2E_FIREBASE_API_KEY}" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"returnSecureToken\":true}" \
    | jq -r '.idToken'
}

echo "==> Signing in as consumer ${E2E_TEST_EMAIL}"
CONSUMER_TOKEN="$(firebase_signin "$E2E_TEST_EMAIL" "$E2E_TEST_PASSWORD")"
[[ -n "$CONSUMER_TOKEN" && "$CONSUMER_TOKEN" != "null" ]] || { echo "Consumer signin failed"; exit 1; }

echo "==> Creating order for variant ${SEEDED_PRODUCT_VARIANT_ID}"
ORDER_RESPONSE="$(curl -sS -X POST "${API_BASE_URL}/v1/orders" \
  -H "Authorization: Bearer ${CONSUMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"items\":[{\"productVariantId\":\"${SEEDED_PRODUCT_VARIANT_ID}\",\"quantity\":1}]}")"
ORDER_ID="$(echo "$ORDER_RESPONSE" | jq -r '.id')"
[[ -n "$ORDER_ID" && "$ORDER_ID" != "null" ]] || { echo "Order create failed: $ORDER_RESPONSE"; exit 1; }
echo "    order_id=${ORDER_ID}"

echo "==> Creating Stripe test-mode payment + simulating webhook"
PAYMENT_RESPONSE="$(curl -sS -X POST "${API_BASE_URL}/v1/payments" \
  -H "Authorization: Bearer ${CONSUMER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"${ORDER_ID}\",\"provider\":\"stripe\",\"paymentMethodToken\":\"tok_visa\"}")"
PAYMENT_ID="$(echo "$PAYMENT_RESPONSE" | jq -r '.id // .paymentId')"
[[ -n "$PAYMENT_ID" && "$PAYMENT_ID" != "null" ]] || { echo "Payment create failed: $PAYMENT_RESPONSE"; exit 1; }

# Signed Stripe webhook simulation. In dev the webhook handler trusts the
# webhook secret in the stripe-test Secret; replay it here with a canned
# payment_intent.succeeded event.
STRIPE_WEBHOOK_SECRET="$(kubectl -n core-services get secret stripe-test -o jsonpath='{.data.webhook-secret}' | base64 -d)"
NOW="$(date +%s)"
PAYLOAD="{\"id\":\"evt_e2e_${NOW}\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_e2e_${NOW}\",\"metadata\":{\"paymentId\":\"${PAYMENT_ID}\"}}}}"
SIG_PAYLOAD="${NOW}.${PAYLOAD}"
SIGNATURE="$(printf '%s' "$SIG_PAYLOAD" | openssl dgst -sha256 -hmac "$STRIPE_WEBHOOK_SECRET" -binary | xxd -p -c 256)"
curl -sS -X POST "${API_BASE_URL}/v1/payments/webhooks/stripe" \
  -H "Stripe-Signature: t=${NOW},v1=${SIGNATURE}" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" > /dev/null

echo "==> Waiting for ticket_pass to materialize"
PASS_QR=""
for i in $(seq 1 30); do
  TICKETS="$(curl -sS -X GET "${API_BASE_URL}/v1/me/tickets?orderId=${ORDER_ID}" \
    -H "Authorization: Bearer ${CONSUMER_TOKEN}")"
  PASS_QR="$(echo "$TICKETS" | jq -r '.[0].qrCodePayload // empty')"
  if [[ -n "$PASS_QR" ]]; then
    echo "    qr=${PASS_QR}"
    break
  fi
  sleep 2
done
[[ -n "$PASS_QR" ]] || { echo "Ticket pass never appeared"; exit 1; }

echo "==> Signing in as scanner ${E2E_SCANNER_EMAIL}"
SCANNER_TOKEN="$(firebase_signin "$E2E_SCANNER_EMAIL" "$E2E_SCANNER_PASSWORD")"
[[ -n "$SCANNER_TOKEN" && "$SCANNER_TOKEN" != "null" ]] || { echo "Scanner signin failed"; exit 1; }

VENUE_ID="$(echo "$SCANNER_VENUE_IDS" | cut -d, -f1)"
IDEMPOTENCY_KEY="$(uuidgen)"
echo "==> Redeeming QR via scanner-op endpoint"
REDEEM_RESPONSE="$(curl -sS -X POST "${API_BASE_URL}/v1/op/tickets/redeem" \
  -H "Authorization: Bearer ${SCANNER_TOKEN}" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"qrCodePayload\":\"${PASS_QR}\",\"scannerDeviceId\":\"e2e-walkthrough\",\"venueId\":\"${VENUE_ID}\"}")"
step "redeem_succeeded" test "$(echo "$REDEEM_RESPONSE" | jq -r '.outcome')" = "REDEEMED"

echo "==> Re-checking ticket status"
POST_STATUS="$(curl -sS -X GET "${API_BASE_URL}/v1/me/tickets?orderId=${ORDER_ID}" \
  -H "Authorization: Bearer ${CONSUMER_TOKEN}" | jq -r '.[0].status')"
step "pass_is_used" test "$POST_STATUS" = "USED"

echo "==> Port-forwarding MailHog for email assertion"
kubectl -n core-services port-forward svc/mailhog "${MAILHOG_PORT_FORWARD}:8025" >/dev/null 2>&1 &
PF_PID=$!
trap 'kill $PF_PID 2>/dev/null || true' EXIT
sleep 2

# Trigger a reservation that will email the consumer — small detour so we
# exercise the SMTP path. Skipped on missing SEEDED_SLOT_ID.
if [[ -n "${SEEDED_SLOT_ID:-}" ]]; then
  RES_RESPONSE="$(curl -sS -X POST "${API_BASE_URL}/v1/reservations" \
    -H "Authorization: Bearer ${CONSUMER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"slotId\":\"${SEEDED_SLOT_ID}\",\"guestName\":\"E2E\",\"guestEmail\":\"${E2E_TEST_EMAIL}\",\"guestCount\":1}")"
  RES_ID="$(echo "$RES_RESPONSE" | jq -r '.id')"
  sleep 5
  INBOX="$(curl -sS "http://localhost:${MAILHOG_PORT_FORWARD}/api/v2/search?kind=to&query=${E2E_TEST_EMAIL}")"
  step "mailhog_captured_reservation_email" test "$(echo "$INBOX" | jq -r '.total')" -gt 0
fi

echo ""
echo "===================================================="
echo "  walkthrough complete: ${pass_count} pass, ${fail_count} fail"
echo "===================================================="
[[ $fail_count -eq 0 ]]
