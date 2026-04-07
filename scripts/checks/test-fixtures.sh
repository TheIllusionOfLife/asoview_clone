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

  # Run the check against each broken fixture file INDIVIDUALLY so that
  # a gap covered by one fixture cannot mask a regression in another.
  # (Devin PR #23 finding: a negative-lookbehind silently disabled the
  # variable-form pass but the meta-test still reported PASS because a
  # separate inline fixture in the same dir kept the exit code
  # non-zero.)
  any_broken_pass=0
  for bf in "$fixture_root/broken/"*; do
    [[ -e "$bf" ]] || continue
    solo_dir="$(mktemp -d)"
    cp "$bf" "$solo_dir/"
    printf '== %-30s ' "$check (broken:$(basename "$bf"))"
    set +e
    out="$(FIXTURES="$solo_dir" "$script" --fixtures 2>&1)"
    rc=$?
    set -e
    rm -rf "$solo_dir"
    if [[ $rc -ne 0 ]]; then
      printf 'PASS\n'
    else
      printf 'FAIL (this specific broken fixture did not trigger)\n'
      printf '%s\n' "$out" | sed 's/^/    /'
      EXIT=1
      any_broken_pass=1
    fi
  done
  if [[ $any_broken_pass -eq 0 && -z "$(ls -A "$fixture_root/broken" 2>/dev/null)" ]]; then
    printf '== %-30s FAIL (no broken fixtures)\n' "$check (broken)"
    EXIT=1
  fi
done

exit "$EXIT"
