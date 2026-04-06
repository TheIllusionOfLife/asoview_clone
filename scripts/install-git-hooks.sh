#!/usr/bin/env bash
#
# Installs a pre-commit hook that runs spotlessApply on staged Java changes
# before allowing the commit to land. Eliminates the round-trip of "push,
# wait for CI to fail spotlessCheck, fix, push again" that bit PR #18 seven
# times.
#
# Usage:
#   ./scripts/install-git-hooks.sh
#
# To uninstall: rm .git/hooks/pre-commit
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
hook_path="${repo_root}/.git/hooks/pre-commit"

cat > "${hook_path}" <<'HOOK'
#!/usr/bin/env bash
# Auto-installed by scripts/install-git-hooks.sh
set -euo pipefail

# Only run when Java files are staged.
staged_java=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.java$' || true)
if [ -z "${staged_java}" ]; then
  exit 0
fi

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

if [ -z "${JAVA_HOME:-}" ] && [ -d "/opt/homebrew/Cellar/openjdk@21" ]; then
  export JAVA_HOME="$(ls -d /opt/homebrew/Cellar/openjdk@21/*/libexec/openjdk.jdk/Contents/Home | head -1)"
fi

echo "[pre-commit] running ./gradlew spotlessApply on staged Java..."
if ! ./gradlew spotlessApply -q; then
  echo "[pre-commit] spotlessApply failed; aborting commit." >&2
  exit 1
fi

# Re-stage any files spotless reformatted (limited to originally staged ones).
echo "${staged_java}" | xargs git add
HOOK

chmod +x "${hook_path}"
echo "Installed pre-commit hook at ${hook_path}"
echo "Future commits with Java changes will automatically run spotlessApply."
