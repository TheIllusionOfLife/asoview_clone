#!/usr/bin/env bash
#
# Meta-test for the pitfall checks: every shell check must pass against
# its `clean/` fixtures and fail (exit non-zero) against its `broken/`
# fixtures. Without this meta-test a typo in a grep pattern silently
# disables the check.
#
# Exit 0 if every check behaves correctly on both fixture sets, exit 1
# otherwise.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECKS_DIR="$REPO_ROOT/scripts/checks"
FIXTURES_DIR="$CHECKS_DIR/__fixtures__"

declare -a CHECKS=(
  "assigned-id-save"
  "money-parsing"
  "modifying-flush-clear"
  "ssr-no-route"
)

EXIT=0

for check in "${CHECKS[@]}"; do
  script="$CHECKS_DIR/$check.sh"
  fixture_root="$FIXTURES_DIR/$check"

  if [[ ! -d "$fixture_root/clean" || ! -d "$fixture_root/broken" ]]; then
    echo "META FAIL  $check  — missing clean/ or broken/ fixture under $fixture_root"
    EXIT=1
    continue
  fi

  printf '== %-30s ' "$check (clean)"
  if FIXTURES="$fixture_root/clean" "$script" --fixtures >/dev/null 2>&1; then
    printf 'PASS\n'
  else
    printf 'FAIL (clean fixture should pass)\n'
    EXIT=1
  fi

  printf '== %-30s ' "$check (broken)"
  if FIXTURES="$fixture_root/broken" "$script" --fixtures >/dev/null 2>&1; then
    printf 'FAIL (broken fixture should fail)\n'
    EXIT=1
  else
    printf 'PASS\n'
  fi
done

exit "$EXIT"
