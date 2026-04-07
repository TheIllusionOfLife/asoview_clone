#!/usr/bin/env bash
#
# Pitfall 1 (PR #21): JpaRepository.save(...) on an entity with an
# assigned @Id (no @GeneratedValue) routes through Hibernate's merge() —
# SELECT + UPDATE — instead of INSERT. Sequential retries (e.g.
# webhook replay) silently succeed instead of throwing
# DataIntegrityViolationException, defeating any catch-based replay
# guard. Use insertIfMissing(...) or saveAndFlush(...) instead.
#
# Approach: build a list of entity classes with @IdClass or with @Id but
# no @GeneratedValue, then ripgrep `\.save\(` call sites where the
# argument is `new <EntityName>(`. Allow-list `\.saveAndFlush\(`.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ "${1:-}" == "--fixtures" ]]; then
  SCAN_ROOTS=("${FIXTURES:?FIXTURES env var required with --fixtures}")
else
  SCAN_ROOTS=("services/commerce-core/src/main/java" "services/commerce-core/src/test/java")
fi

# Step 1: discover entity class names with assigned @Id (no @GeneratedValue)
# or @IdClass. Heuristic: any .java file containing both @Id (or @IdClass)
# and @Entity, and NOT containing @GeneratedValue.
ENTITY_NAMES=()
while IFS= read -r f; do
  # Extract every class name declared in the file. Most JPA entities are
  # one class per file, but we tolerate multiple just in case.
  while IFS= read -r name; do
    [[ -n "$name" ]] && ENTITY_NAMES+=("$name")
  done < <(rg -o --no-filename '^\s*(?:public\s+)?(?:abstract\s+)?class\s+(\w+)' --replace '$1' "$f" 2>/dev/null || true)
done < <(
  for root in "${SCAN_ROOTS[@]}"; do
    [[ -d "$root" ]] || continue
    rg -l --no-messages '@(IdClass|Entity)' "$root" 2>/dev/null
  done | sort -u | while read -r f; do
    if rg -q '@IdClass' "$f" 2>/dev/null; then
      echo "$f"
      continue
    fi
    if rg -q '@Id' "$f" 2>/dev/null && rg -q '@Entity' "$f" 2>/dev/null && ! rg -q '@GeneratedValue' "$f" 2>/dev/null; then
      echo "$f"
    fi
  done
)

if [[ ${#ENTITY_NAMES[@]} -eq 0 ]]; then
  exit 0
fi

# Build the alternation, e.g. (Foo|Bar|Baz)
joined="$(IFS='|'; echo "${ENTITY_NAMES[*]}")"
PATTERN="\\.save\\(\\s*new (${joined})\\("

VIOLATIONS=""
for root in "${SCAN_ROOTS[@]}"; do
  [[ -d "$root" ]] || continue
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    # Allow-list saveAndFlush
    if echo "$line" | rg -q '\.saveAndFlush\('; then
      continue
    fi
    VIOLATIONS+="$line"$'\n'
  done < <(rg -n --no-heading "$PATTERN" "$root" 2>/dev/null || true)
done

if [[ -n "$VIOLATIONS" ]]; then
  echo "FAIL assigned-id-save: JpaRepository.save() on assigned-@Id entity routes through merge() —"
  echo "  use insertIfMissing(...) or saveAndFlush(...). See CLAUDE.md 'Review Pitfalls (PR #21)'."
  echo
  echo "$VIOLATIONS" | sed 's/^/  /'
  exit 1
fi

exit 0
