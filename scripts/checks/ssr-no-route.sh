#!/usr/bin/env bash
#
# Pitfall 15 (PR #22): Playwright `page.route(...)` installs a network
# interceptor in the BROWSER. Next.js SSR pages fetch their data from a
# Node process before the browser is involved, so page.route() can't
# intercept those requests. Specs that mock SSR fetches via page.route
# silently never see the mocked response and time out.
#
# Enforced via directory split: e2e/csr/ may use page.route(...),
# e2e/ssr/ may NOT. This check is one rg invocation.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ "${1:-}" == "--fixtures" ]]; then
  SCAN_ROOT="${FIXTURES:?FIXTURES env var required with --fixtures}"
else
  SCAN_ROOT="apps/asoview-web/e2e/ssr"
fi

[[ -d "$SCAN_ROOT" ]] || exit 0

MATCHES="$(rg -n --no-heading --no-ignore 'page\.route\(' "$SCAN_ROOT" 2>/dev/null || true)"

if [[ -n "$MATCHES" ]]; then
  echo "FAIL ssr-no-route: Playwright page.route() cannot intercept Next.js SSR fetches —"
  echo "  they run in Node before the browser is involved. Move this spec to e2e/csr/, OR"
  echo "  switch the page to CSR, OR drive real backend traffic. See CLAUDE.md 'Review"
  echo "  Pitfalls (PR #22)'."
  echo
  echo "$MATCHES" | sed 's/^/  /'
  exit 1
fi

exit 0
