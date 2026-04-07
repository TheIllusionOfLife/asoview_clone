/**
 * Pitfall 16 (PR 3d.5): the SSR routes manifest at apps/asoview-web/ssr-routes.txt
 * is consumed by `scripts/checks/ssr-no-route.sh` and the Playwright csr/ssr
 * directory split. If a page in the manifest silently loses its
 * `force-dynamic` / `revalidate` directive, the SSR semantics drift away
 * from the manifest and the lint guard becomes stale. This test asserts the
 * manifest matches reality.
 */

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const REPO_WEB_ROOT = join(__dirname, "..", "..", "..");

function manifestRoutes(): string[] {
  const raw = readFileSync(join(REPO_WEB_ROOT, "ssr-routes.txt"), "utf-8");
  return raw
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));
}

function pageFileFor(route: string): string {
  // Translate "/areas/[area]" → src/app/areas/[area]/page.tsx
  // Translate "/" → src/app/page.tsx
  const segments = route === "/" ? [] : route.replace(/^\//, "").split("/");
  return join(REPO_WEB_ROOT, "src", "app", ...segments, "page.tsx");
}

describe("SSR manifest matches reality (Pitfall 16)", () => {
  const routes = manifestRoutes();

  it("manifest is non-empty", () => {
    expect(routes.length).toBeGreaterThan(0);
  });

  it.each(routes)("%s — page file declares force-dynamic or revalidate", (route) => {
    const file = pageFileFor(route);
    const content = readFileSync(file, "utf-8");
    const hasDynamic = /export\s+const\s+dynamic\s*=\s*["']force-dynamic["']/.test(content);
    const hasRevalidate = /export\s+const\s+revalidate\s*=/.test(content);
    expect(
      hasDynamic || hasRevalidate,
      `${route} (${file}) must declare force-dynamic or revalidate`,
    ).toBe(true);
  });
});
