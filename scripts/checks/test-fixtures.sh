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
  set +e
  clean_out="$(FIXTURES="$fixture_root/clean" "$script" --fixtures 2>&1)"
  clean_rc=$?
  set -e
  if [[ $clean_rc -eq 0 ]]; then
    printf 'PASS\n'
  else
    printf 'FAIL (clean fixture should pass)\n'
    printf '%s\n' "$clean_out" | sed 's/^/    /'
    EXIT=1
  fi

  printf '== %-30s ' "$check (broken)"
  set +e
  broken_out="$(FIXTURES="$fixture_root/broken" "$script" --fixtures 2>&1)"
  broken_rc=$?
  set -e
  if [[ $broken_rc -ne 0 ]]; then
    printf 'PASS\n'
  else
    printf 'FAIL (broken fixture should fail)\n'
    printf '%s\n' "$broken_out" | sed 's/^/    /'
    EXIT=1
  fi
done

exit "$EXIT"
