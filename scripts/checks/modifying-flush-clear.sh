#!/usr/bin/env bash
#
# Pitfall 3 (PR #21): @Modifying JPA queries should set both
# clearAutomatically=true and flushAutomatically=true so the
# persistence context is consistent with the database after the
# update. Without flushAutomatically a CAS retry loop reads stale
# entities; without clearAutomatically a subsequent findById returns
# the pre-update value.
#
# Approach: ripgrep every `@Modifying` annotation under
# services/commerce-core/src/main/java and assert it has both flags.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ "${1:-}" == "--fixtures" ]]; then
  SCAN_ROOT="${FIXTURES:?FIXTURES env var required with --fixtures}"
else
  SCAN_ROOT="services/commerce-core/src/main/java"
fi

[[ -d "$SCAN_ROOT" ]] || exit 0

VIOLATIONS=""
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  # The match looks like:  /path/to/Foo.java:42:  @Modifying
  # Or:                    /path/to/Foo.java:42:  @Modifying(clearAutomatically=true)
  # We pass if BOTH flags are present on the same line.
  if echo "$line" | rg -q 'clearAutomatically\s*=\s*true' && \
     echo "$line" | rg -q 'flushAutomatically\s*=\s*true'; then
    continue
  fi
  VIOLATIONS+="$line"$'\n'
done < <(rg -n --no-heading --no-ignore '^\s*@Modifying' "$SCAN_ROOT" 2>/dev/null || true)

if [[ -n "$VIOLATIONS" ]]; then
  echo "FAIL modifying-flush-clear: @Modifying JPA queries must set both"
  echo "  clearAutomatically=true and flushAutomatically=true to keep the persistence context"
  echo "  consistent with the database. See CLAUDE.md 'Review Pitfalls (PR #21)'."
  echo
  echo "$VIOLATIONS" | sed 's/^/  /'
  exit 1
fi

exit 0
