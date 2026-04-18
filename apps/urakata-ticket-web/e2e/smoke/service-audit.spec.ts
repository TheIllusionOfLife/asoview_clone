import { expect, test } from "@playwright/test";

/**
 * Live-cluster audit for urakata-ticket-web (consumer ticket display).
 *
 * Invoked manually or by the scheduled dev-audit.yml workflow:
 *   PLAYWRIGHT_BASE_URL=https://asoview-tickets.duckdns.org \
 *   API_BASE_URL=https://asoview-tickets.duckdns.org/api \
 *   E2E_FIREBASE_API_KEY=... E2E_TEST_EMAIL=... E2E_TEST_PASSWORD=... \
 *   bunx playwright test --config=playwright.audit.config.ts
 *
 * Scope: prove the app loads end-to-end at the real subdomain and the
 * per-user ticket-list endpoint is reachable under the consumer flow.
 * Deeper flows (QR render on a seeded pass, validity gating) remain
 * in apps/asoview-web/e2e/csr/validity.spec.ts where mocks are fine.
 */

const apiBase = process.env.API_BASE_URL;
const FIREBASE_API_KEY = process.env.E2E_FIREBASE_API_KEY;
const TEST_EMAIL = process.env.E2E_TEST_EMAIL;
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD;

async function signIn(): Promise<string> {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD, returnSecureToken: true }),
    },
  );
  if (!res.ok) throw new Error(`Firebase sign-in failed: ${await res.text()}`);
  const data = (await res.json()) as { idToken?: string };
  if (!data.idToken) throw new Error("No idToken in Firebase response");
  return data.idToken;
}

let token = "";

test.beforeAll(async () => {
  if (!apiBase) throw new Error("API_BASE_URL required");
  if (!FIREBASE_API_KEY || !TEST_EMAIL || !TEST_PASSWORD) {
    throw new Error("E2E_FIREBASE_API_KEY, E2E_TEST_EMAIL, E2E_TEST_PASSWORD required");
  }
  token = await signIn();
});

test.describe("urakata-ticket-web live audit", () => {
  test("landing renders at the production subdomain", async ({ page }) => {
    const res = await page.goto("/");
    expect(res?.status()).toBeLessThan(400);
    // HTML shell shouldn't leak Next.js build errors. Anything under an
    // <html lang=...> root counts as "rendered OK" for this smoke level.
    await expect(page.locator("html")).toHaveAttribute("lang", /ja|en/);
  });

  test("GET /v1/me/tickets returns a list for the test user", async ({ request }) => {
    const r = await request.get(`${apiBase}/v1/me/tickets`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(r.status()).toBe(200);
    const body = (await r.json()) as unknown;
    expect(Array.isArray(body)).toBeTruthy();
  });
});
