#!/usr/bin/env bash
#
# Pitfall 3 (PR #21): @Modifying JPA queries should set both
# clearAutomatically=true and flushAutomatically=true so the
# persistence context is consistent with the database after the
# update. Without flushAutomatically a CAS retry loop reads stale
# entities; without clearAutomatically a subsequent findById returns
# the pre-update value.
#
# Approach: ripgrep each `@Modifying` annotation in multiline mode so
# the flags can live on continuation lines (common after IDE
# reformatting). Reject any match that doesn't contain BOTH flags
# somewhere inside the annotation's parens.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ "${1:-}" == "--fixtures" ]]; then
  SCAN_ROOT="${FIXTURES:?FIXTURES env var required with --fixtures}"
else
  SCAN_ROOT="services/commerce-core/src/main/java"
fi

[[ -d "$SCAN_ROOT" ]] || exit 0

# Multiline-match:
#   ^\s*@Modifying([^)]*)
# capturing everything up to the closing paren. Then assert both flags
# are somewhere inside the capture. A bare `@Modifying` (no parens)
# also fails.
VIOLATIONS=""
while IFS= read -r match; do
  [[ -z "$match" ]] && continue
  # Bare @Modifying without parens is a violation.
  if ! echo "$match" | rg -q 'clearAutomatically\s*=\s*true'; then
    VIOLATIONS+="$match"$'\n\n'
    continue
  fi
  if ! echo "$match" | rg -q 'flushAutomatically\s*=\s*true'; then
    VIOLATIONS+="$match"$'\n\n'
    continue
  fi
done < <(
  rg -U --multiline-dotall --no-heading --line-number --no-ignore \
    '^\s*@Modifying(\([^)]*\))?' "$SCAN_ROOT" 2>/dev/null || true
)

if [[ -n "$VIOLATIONS" ]]; then
  echo "FAIL modifying-flush-clear: @Modifying JPA queries must set both"
  echo "  clearAutomatically=true and flushAutomatically=true. Multiline"
  echo "  annotations are tolerated — the check reads each @Modifying(...)"
  echo "  paren group as a whole. See CLAUDE.md 'Review Pitfalls (PR #21)'."
  echo
  # shellcheck disable=SC2001
  echo "$VIOLATIONS" | sed 's/^/  /'
  exit 1
fi

exit 0
