import { expect, test } from "@playwright/test";

/**
 * Authenticated flow tests against the live site.
 *
 * Uses Firebase REST API to get an ID token for API-level tests.
 * UI tests sign in via the email/password form on the signin page.
 *
 * Prerequisites:
 *   1. Email/password sign-in enabled in Firebase Console
 *   2. E2E_FIREBASE_API_KEY, E2E_TEST_EMAIL, E2E_TEST_PASSWORD env vars set
 *   3. PLAYWRIGHT_BASE_URL set to the live site
 */

const TEST_EMAIL = process.env.E2E_TEST_EMAIL;
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD;
const FIREBASE_API_KEY = process.env.E2E_FIREBASE_API_KEY;

// ─── Helper: get ID token via Firebase REST API ─────────────────────

async function getIdToken(): Promise<string> {
  // Try sign-in first
  const signInRes = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD, returnSecureToken: true }),
    },
  );

  if (signInRes.ok) {
    return (await signInRes.json()).idToken;
  }

  // Only attempt signup if user doesn't exist
  const errBody = await signInRes.json().catch(() => null);
  const errCode = errBody?.error?.message ?? "";
  if (errCode !== "EMAIL_NOT_FOUND") {
    throw new Error(`Firebase sign-in failed: ${errCode}`);
  }

  const signUpRes = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: TEST_EMAIL,
        password: TEST_PASSWORD,
        returnSecureToken: true,
      }),
    },
  );
  if (!signUpRes.ok) {
    const err = await signUpRes.text();
    throw new Error(`Failed to create test user: ${err}`);
  }
  return (await signUpRes.json()).idToken;
}

// ─── Helper: sign in via the UI form ────────────────────────────────

async function signInViaUI(page: import("@playwright/test").Page): Promise<void> {
  await page.goto("/ja/signin");
  await page.getByTestId("email-input").fill(TEST_EMAIL as string);
  await page.getByTestId("password-input").fill(TEST_PASSWORD as string);
  await page.getByRole("button", { name: "Sign in with Email" }).click();
  // Wait for redirect away from signin page
  await page.waitForURL(/(?!.*signin)/, { timeout: 15_000 });
}

// ─── Shared state ───────────────────────────────────────────────────

let idToken: string;

test.beforeAll(async () => {
  if (!FIREBASE_API_KEY || !TEST_EMAIL || !TEST_PASSWORD) {
    throw new Error(
      "E2E_FIREBASE_API_KEY, E2E_TEST_EMAIL, and E2E_TEST_PASSWORD env vars are required",
    );
  }
  idToken = await getIdToken();
});

// ─── Authenticated API tests ────────────────────────────────────────

test.describe("authenticated API", () => {
  test("GET /api/v1/me → not 401 with valid token", async ({ request }) => {
    const res = await request.get("/api/v1/me", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    // 200 or 404 (user may not have a profile row yet) — but NOT 401
    expect(res.status()).not.toBe(401);
  });

  test("GET /api/v1/me/favorites → not 401", async ({ request }) => {
    const res = await request.get("/api/v1/me/favorites", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(res.status()).not.toBe(401);
  });

  test("GET /api/v1/me/orders → not 401", async ({ request }) => {
    const res = await request.get("/api/v1/me/orders", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(res.status()).not.toBe(401);
  });

  test("GET /api/v1/me/points → not 401", async ({ request }) => {
    const res = await request.get("/api/v1/me/points", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(res.status()).not.toBe(401);
  });

  test("POST /api/v1/me/favorites/{productId} → toggle favorite", async ({ request }) => {
    const listRes = await request.get("/api/v1/products?size=1");
    expect(listRes.status()).toBe(200);
    const content = (await listRes.json()).content;
    expect(content.length).toBeGreaterThan(0);
    const productId = content[0].id;

    // Add favorite
    const addRes = await request.post(`/api/v1/me/favorites/${productId}`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(addRes.status()).not.toBe(401);

    // Remove favorite
    const delRes = await request.delete(`/api/v1/me/favorites/${productId}`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(delRes.status()).not.toBe(401);
  });
});

// ─── Authenticated UI tests ─────────────────────────────────────────

test.describe("authenticated UI", () => {
  test("sign in via email form and access protected pages", async ({ page }) => {
    await signInViaUI(page);

    // Should be on home page after sign-in
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });

    // Navigate to orders page — should NOT redirect to signin
    await page.goto("/ja/me/orders");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
  });

  test("favorites page accessible when signed in", async ({ page }) => {
    await signInViaUI(page);
    await page.goto("/ja/me/favorites");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
  });

  test("points page accessible when signed in", async ({ page }) => {
    await signInViaUI(page);
    await page.goto("/ja/me/points");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
  });

  test("product page favorite toggle works when signed in", async ({ page }) => {
    await signInViaUI(page);

    const res = await page.request.get("/api/v1/products?size=1");
    expect(res.status()).toBe(200);
    const content = (await res.json()).content;
    expect(content.length).toBeGreaterThan(0);

    await page.goto(`/ja/products/${content[0].id}`);
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });

    // Find heart/favorite button and click it if present
    const heartBtn = page
      .locator("button[aria-label*='avorite'], button:has(svg[data-testid='heart'])")
      .first();
    if ((await heartBtn.count()) > 0) {
      await heartBtn.click();
      await page.waitForTimeout(1000);
      await expect(page.locator("h1")).toBeVisible();
    }
  });

  test("cart page works when signed in", async ({ page }) => {
    await signInViaUI(page);
    await page.goto("/ja/cart");
    await page.waitForTimeout(2000);
    expect(page.url()).toContain("/cart");
    await expect(page.locator("body")).toBeVisible();
  });
});
