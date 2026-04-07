#!/usr/bin/env bash
#
# Pitfall enforcement orchestrator. Runs every shell check under
# scripts/checks/*.sh AND the ArchUnit rule suite under
# services/commerce-core via Gradle. See PR 3d.5.
#
# Exit 0 if all checks pass, exit 1 if any failed. Prints a summary
# table at the end.
#
# Each shell check is self-contained: `set -euo pipefail`, exits non-zero
# with `FAIL <name>: <message>` on violation, exits 0 silently on clean.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

CHECKS_DIR="$REPO_ROOT/scripts/checks"
declare -a SHELL_CHECKS=(
  "assigned-id-save.sh"
  "money-parsing.sh"
  "modifying-flush-clear.sh"
  "ssr-no-route.sh"
)

declare -a RESULTS=()
EXIT=0

for check in "${SHELL_CHECKS[@]}"; do
  printf '== %-32s ' "$check"
  if "$CHECKS_DIR/$check"; then
    printf 'PASS\n'
    RESULTS+=("PASS  $check")
  else
    printf 'FAIL\n'
    RESULTS+=("FAIL  $check")
    EXIT=1
  fi
done

# ArchUnit rules — opt-out if ARCH=skip is set (used by the meta-test
# fixture runner that only exercises the shell checks).
if [[ "${ARCH:-run}" != "skip" ]]; then
  # Best-effort JDK 21 discovery on macOS if JAVA_HOME isn't set. CI
  # already sets it via setup-java.
  if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in \
      /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
      /usr/lib/jvm/java-21-openjdk-amd64 \
      /usr/lib/jvm/temurin-21-jdk-amd64; do
      if [[ -x "$candidate/bin/java" ]]; then
        export JAVA_HOME="$candidate"
        break
      fi
    done
  fi

  printf '== %-32s ' "ArchUnit rules (Gradle)"
  if ./gradlew :services:commerce-core:test --tests "com.asoviewclone.commercecore.arch.*" >/tmp/archunit.log 2>&1; then
    printf 'PASS\n'
    RESULTS+=("PASS  archunit")
  else
    printf 'FAIL\n'
    RESULTS+=("FAIL  archunit")
    cat /tmp/archunit.log | tail -40
    EXIT=1
  fi
fi

echo
echo "lint-pitfalls summary:"
for r in "${RESULTS[@]}"; do
  echo "  $r"
done

exit "$EXIT"
