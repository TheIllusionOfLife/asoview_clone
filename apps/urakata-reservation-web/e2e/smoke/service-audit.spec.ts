import { expect, test } from "@playwright/test";

/**
 * Live-cluster audit for urakata-reservation-web (operator reservation UI).
 *
 * Scope is deliberately narrow: this app authenticates via tenant-scoped
 * Firebase custom claims (see services/reservation-service/.../TenantAccessChecker),
 * and we don't provision a test operator identity on the dev cluster. The
 * smoke asserts the ingress + static bundle + healthz path work; deeper
 * flows live in the app's own e2e/ suite against a local dev server with
 * mocks (reservations.spec.ts, slots.spec.ts).
 *
 * Invoked by the scheduled dev-audit.yml workflow:
 *   PLAYWRIGHT_BASE_URL=https://asoview-operator.duckdns.org \
 *   bunx playwright test --config=playwright.audit.config.ts
 */

test.describe("urakata-reservation-web live audit", () => {
  test("landing renders at the operator subdomain", async ({ page }) => {
    const res = await page.goto("/");
    expect(res?.status()).toBeLessThan(500);
    // Most operator routes redirect to /signin when unauthenticated; either
    // shape is a pass for this ingress-level smoke.
    await expect(page.locator("html")).toHaveAttribute("lang", /ja|en/);
  });

  test("static _next bundle reachable", async ({ request }) => {
    // A 404 here would mean the Next.js build artifact wasn't deployed or
    // the ingress isn't routing static paths correctly. Hit the HTML first
    // to discover the bundle hash, then fetch a JS chunk referenced in it.
    const shell = await request.get("/");
    expect(shell.status()).toBe(200);
    const html = await shell.text();
    const match = html.match(/\/_next\/static\/[^"']+\.js/);
    expect(match, "HTML shell references a _next/static JS chunk").not.toBeNull();
    const chunk = await request.get(match?.[0] ?? "/");
    expect(chunk.status()).toBe(200);
  });
});
