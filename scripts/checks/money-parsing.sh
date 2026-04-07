#!/usr/bin/env bash
#
# Pitfall 2 (PR #21/#22): NUMERIC money strings from the backend (e.g.
# "1500.50") must be parsed as integer minor units (yen × 100 + sen),
# not via parseFloat / Number / Math.trunc on the cents side. Otherwise
# fractional yen are silently lost on rounding.
#
# Approach: ripgrep `parseFloat(` and `Number(` in any TS file under
# apps/asoview-web/src whose path mentions cart, price, payment, order,
# points, wallet, subtotal, or amount. Allow-list lines containing
# `parseMinorUnits(` or `// money-parse-ok`.
#
# Math.trunc is intentionally NOT in the deny list: on its own it just
# truncates a number, and the legitimate cart.subtotal implementation
# uses it to split minor-units back into yen+sen for display. The
# dangerous shape is `Math.trunc(parseFloat(...))` which would already
# be caught by the parseFloat scan.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ "${1:-}" == "--fixtures" ]]; then
  SCAN_ROOT="${FIXTURES:?FIXTURES env var required with --fixtures}"
else
  SCAN_ROOT="apps/asoview-web/src"
fi

[[ -d "$SCAN_ROOT" ]] || exit 0

VIOLATIONS=""
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  # Allow-list
  if echo "$line" | rg -q 'parseMinorUnits\(|money-parse-ok'; then
    continue
  fi
  VIOLATIONS+="$line"$'\n'
done < <(
  # Match money-handling files by any path segment, not just basename:
  # includes e.g. `cart/utils/index.ts`. rg glob `**/cart*/**/*.ts` lets
  # the keyword live anywhere in the path tree.
  rg -n --no-heading --no-ignore \
    -g '**/*cart*.ts' -g '**/*cart*.tsx' -g '**/cart/**/*.ts' -g '**/cart/**/*.tsx' \
    -g '**/*price*.ts' -g '**/*price*.tsx' -g '**/price/**/*.ts' -g '**/price/**/*.tsx' \
    -g '**/*payment*.ts' -g '**/*payment*.tsx' -g '**/payments/**/*.ts' -g '**/payments/**/*.tsx' \
    -g '**/*order*.ts' -g '**/*order*.tsx' -g '**/orders/**/*.ts' -g '**/orders/**/*.tsx' \
    -g '**/*points*.ts' -g '**/*points*.tsx' -g '**/points/**/*.ts' -g '**/points/**/*.tsx' \
    -g '**/*wallet*.ts' -g '**/*wallet*.tsx' -g '**/wallet/**/*.ts' -g '**/wallet/**/*.tsx' \
    -g '**/*subtotal*.ts' -g '**/*subtotal*.tsx' -g '**/subtotal/**/*.ts' -g '**/subtotal/**/*.tsx' \
    -g '**/*amount*.ts' -g '**/*amount*.tsx' -g '**/amount/**/*.ts' -g '**/amount/**/*.tsx' \
    '(parseFloat\(|Number\()' "$SCAN_ROOT" 2>/dev/null || true
)

if [[ -n "$VIOLATIONS" ]]; then
  echo "FAIL money-parsing: NUMERIC money strings need integer-minor-units parsing,"
  echo "  not parseFloat/Number/Math.trunc. Use parseMinorUnits(...) from cart.ts or"
  echo "  add a '// money-parse-ok' line comment if this site genuinely doesn't handle money."
  echo "  See CLAUDE.md 'Review Pitfalls (PR #21)' / 'PR #22'."
  echo
  echo "$VIOLATIONS" | sed 's/^/  /'
  exit 1
fi

exit 0
