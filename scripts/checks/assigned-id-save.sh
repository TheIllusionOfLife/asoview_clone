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
  # one class per file, but we tolerate multiple just in case. The
  # regex matches any modifier combination before `class Name` so
  # `public final class Foo`, `abstract class Bar`, and plain `class Baz`
  # are all covered.
  while IFS= read -r name; do
    [[ -n "$name" ]] && ENTITY_NAMES+=("$name")
  done < <(rg -o --no-filename '\bclass\s+(\w+)' --replace '$1' "$f" 2>/dev/null || true)
done < <(
  for root in "${SCAN_ROOTS[@]}"; do
    [[ -d "$root" ]] || continue
    rg -l --no-messages --no-ignore '@(IdClass|Entity)' "$root" 2>/dev/null
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
# Two patterns:
#   - inline:  repo.save(new Foo(...))
#   - assigned-@Id entity as local var: Foo e = new Foo(...);  repo.save(e)
#
# For the variable form we collect local var declarations of type
# <Foo> and then match .save(<varname>). Simple, single-file scope
# (we don't cross-reference across files to keep the check line-local).
INLINE_PATTERN="\\.save\\(\\s*new\\s+(${joined})\\("

VIOLATIONS=""
for root in "${SCAN_ROOTS[@]}"; do
  [[ -d "$root" ]] || continue

  # Inline matches.
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    VIOLATIONS+="$line"$'\n'
  done < <(rg -n --no-heading --no-ignore "$INLINE_PATTERN" "$root" 2>/dev/null || true)

  # Variable-form matches. For each .java file, learn local variable
  # names declared as `<EntityName> varName` or `var varName =
  # new EntityName(`, then grep `.save(varName)`.
  while IFS= read -r f; do
    # Collect (type, varname) declarations. Two passes:
    # 1. Explicit type:  Foo b = new Foo(...);  /  Foo b;
    # 2. Java 10+ var:   var b = new Foo(...);
    local_vars=()
    while IFS= read -r decl; do
      [[ -z "$decl" ]] && continue
      local_vars+=("$decl")
    done < <(
      rg --no-heading --no-filename --no-ignore \
        -o "\\b(${joined})\\s+(\\w+)\\s*(?:=|;)" \
        --replace '$2' "$f" 2>/dev/null || true
    )
    while IFS= read -r decl; do
      [[ -z "$decl" ]] && continue
      local_vars+=("$decl")
    done < <(
      rg --no-heading --no-filename --no-ignore \
        -o "\\bvar\\s+(\\w+)\\s*=\\s*new\\s+(${joined})\\b" \
        --replace '$1' "$f" 2>/dev/null || true
    )
    [[ ${#local_vars[@]} -eq 0 ]] && continue
    # Dedupe in case both passes matched the same variable name.
    # bash 3.2 (macOS) lacks `readarray`; use a portable while-read loop.
    if [[ ${#local_vars[@]} -gt 1 ]]; then
      deduped=()
      while IFS= read -r v; do
        deduped+=("$v")
      done < <(printf '%s\n' "${local_vars[@]}" | awk '!seen[$0]++')
      local_vars=("${deduped[@]}")
    fi
    for v in "${local_vars[@]}"; do
      # Deny-list: .save(v)  — but allow .saveAndFlush(v).
      # Ripgrep 13.x (shipped by Ubuntu 22.04 apt) does not support the
      # negative-lookbehind syntax `(?<!saveAndFlush)` without PCRE2, and
      # the regex error is silently swallowed by `|| true`, disabling the
      # entire variable-form pass (Devin PR #23 finding). Instead match
      # the simpler pattern and post-filter out saveAndFlush lines.
      while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        if echo "$line" | rg -q '\.saveAndFlush\('; then
          continue
        fi
        VIOLATIONS+="$line"$'\n'
      done < <(
        rg -n --no-heading --no-ignore "\\.save\\(\\s*${v}\\s*\\)" "$f" 2>/dev/null || true
      )
    done
  done < <(
    rg -l --no-messages --no-ignore -g '*.java' "\\b(${joined})\\b" "$root" 2>/dev/null || true
  )
done

# NOTE on saveAndFlush: Codex flagged that saveAndFlush STILL routes
# through merge() for assigned-@Id entities and thus cannot reliably
# throw DataIntegrityViolationException on replay. The structural fix
# is `insertIfMissing` — saveAndFlush is a last-resort workaround for
# code that genuinely wants JPA merge semantics. We do NOT warn on
# saveAndFlush here to avoid flooding the ledger + saga code that
# relies on it; upgrading to insertIfMissing is tracked as a follow-up
# refactor in the PR body.

if [[ -n "$VIOLATIONS" ]]; then
  echo "FAIL assigned-id-save: JpaRepository.save() on assigned-@Id entity routes through merge() —"
  echo "  use insertIfMissing(...) or saveAndFlush(...). See CLAUDE.md 'Review Pitfalls (PR #21)'."
  echo
  echo "$VIOLATIONS" | sed 's/^/  /'
  exit 1
fi

exit 0
