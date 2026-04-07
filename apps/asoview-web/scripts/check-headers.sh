#!/usr/bin/env bash
# Smoke check that the dev server emits the security headers from
# next.config.ts. Run after `bun run dev` (or `bun run start`) is up.
#
# Usage: ./scripts/check-headers.sh [base-url]
# Default base-url: http://localhost:3000
set -euo pipefail

BASE="${1:-http://localhost:3000}"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

curl -sI "$BASE/" > "$TMP"

required=(
  "content-security-policy"
  "strict-transport-security"
  "referrer-policy"
  "x-content-type-options"
  "permissions-policy"
)

missing=()
for h in "${required[@]}"; do
  if ! grep -qi "^$h:" "$TMP"; then
    missing+=("$h")
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo "Missing headers from $BASE/:"
  for h in "${missing[@]}"; do echo "  - $h"; done
  echo ""
  echo "Full response headers:"
  cat "$TMP"
  exit 1
fi

echo "OK: all security headers present at $BASE/"
