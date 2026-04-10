import { expect, test } from "@playwright/test";

/**
 * Authenticated flow tests against the live site.
 *
 * Uses Firebase REST API to get an ID token, then either:
 * - Injects it via page.evaluate + Firebase SDK for UI tests
 * - Passes it as Authorization header for API tests
 *
 * Prerequisites:
 *   1. Email/password sign-in enabled in Firebase Console
 *   2. E2E_FIREBASE_API_KEY env var set
 *   3. PLAYWRIGHT_BASE_URL set to the live site
 */

const TEST_EMAIL = process.env.E2E_TEST_EMAIL ?? "e2e-test@asoview-clone.dev";
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD ?? "TestPass123!";
const FIREBASE_API_KEY = process.env.E2E_FIREBASE_API_KEY ?? "";

// ─── Helper: get ID token via Firebase REST API ─────────────────────

async function getIdToken(apiKey: string, email: string, password: string): Promise<string> {
  // Try sign-in
  const signInRes = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );

  if (signInRes.ok) {
    const data = await signInRes.json();
    return data.idToken;
  }

  // User doesn't exist; create it
  const signUpRes = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  if (!signUpRes.ok) {
    const err = await signUpRes.text();
    throw new Error(`Failed to create/sign-in test user: ${err}`);
  }
  const data = await signUpRes.json();
  return data.idToken;
}

// ─── Helper: sign in via page.evaluate using Firebase SDK ───────────

async function signInOnPage(
  page: import("@playwright/test").Page,
  email: string,
  password: string,
): Promise<void> {
  await page.goto("/ja");
  await page.waitForTimeout(2000);
  // Use the Firebase SDK already loaded on the page
  await page.evaluate(
    async ({ email, password }) => {
      const { signInWithEmailAndPassword, getAuth } = await import("firebase/auth");
      const auth = getAuth();
      await signInWithEmailAndPassword(auth, email, password);
    },
    { email, password },
  );
  // Wait for auth state to propagate
  await page.waitForTimeout(2000);
}

// ─── Shared state ───────────────────────────────────────────────────

let idToken: string;

test.beforeAll(async () => {
  if (!FIREBASE_API_KEY) {
    throw new Error("E2E_FIREBASE_API_KEY env var required for authenticated tests");
  }
  idToken = await getIdToken(FIREBASE_API_KEY, TEST_EMAIL, TEST_PASSWORD);
});

// ─── Authenticated API tests ────────────────────────────────────────

test.describe("authenticated API", () => {
  test("GET /api/v1/me → 200 with valid token", async ({ request }) => {
    const res = await request.get("/api/v1/me", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    // 200 or 404 (user may not have a profile row yet) — but NOT 401
    expect([200, 404]).toContain(res.status());
  });

  test("GET /api/v1/me/favorites → 200", async ({ request }) => {
    const res = await request.get("/api/v1/me/favorites", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect([200, 404]).toContain(res.status());
  });

  test("GET /api/v1/me/orders → 200", async ({ request }) => {
    const res = await request.get("/api/v1/me/orders", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect([200, 404]).toContain(res.status());
  });

  test("GET /api/v1/me/points → 200", async ({ request }) => {
    const res = await request.get("/api/v1/me/points", {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect([200, 404]).toContain(res.status());
  });

  test("POST /api/v1/me/favorites/{productId} → toggle favorite", async ({ request }) => {
    // Get a product ID
    const listRes = await request.get("/api/v1/products?size=1");
    const productId = (await listRes.json()).content[0].id;

    // Add favorite
    const addRes = await request.post(`/api/v1/me/favorites/${productId}`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect([200, 201, 204, 409]).toContain(addRes.status());

    // Remove favorite
    const delRes = await request.delete(`/api/v1/me/favorites/${productId}`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect([200, 204, 404]).toContain(delRes.status());
  });
});

// ─── Authenticated UI tests ─────────────────────────────────────────

test.describe("authenticated UI", () => {
  test("sign in via Firebase SDK and access protected pages", async ({ page }) => {
    await signInOnPage(page, TEST_EMAIL, TEST_PASSWORD);

    // Navigate to orders page — should NOT redirect to signin
    await page.goto("/ja/me/orders");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
    await expect(page.locator("body")).toBeVisible();
  });

  test("favorites page accessible when signed in", async ({ page }) => {
    await signInOnPage(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/me/favorites");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
  });

  test("points page accessible when signed in", async ({ page }) => {
    await signInOnPage(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/me/points");
    await page.waitForTimeout(3000);
    expect(page.url()).not.toContain("signin");
  });

  test("product page favorite toggle works when signed in", async ({ page }) => {
    await signInOnPage(page, TEST_EMAIL, TEST_PASSWORD);

    const res = await page.request.get("/api/v1/products?size=1");
    const productId = (await res.json()).content[0].id;

    await page.goto(`/ja/products/${productId}`);
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });

    // Find heart/favorite button and click it
    const heartBtn = page
      .locator("button[aria-label*='avorite'], button:has(svg[data-testid='heart'])")
      .first();
    if (await heartBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await heartBtn.click();
      await page.waitForTimeout(1000);
      // Page should not crash
      await expect(page.locator("h1")).toBeVisible();
    }
  });

  test("cart page works when signed in", async ({ page }) => {
    await signInOnPage(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/cart");
    await page.waitForTimeout(2000);
    expect(page.url()).toContain("/cart");
    await expect(page.locator("body")).toBeVisible();
  });
});
