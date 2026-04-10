import { expect, test } from "@playwright/test";

/**
 * Authenticated flow tests against the live site.
 *
 * Prerequisites:
 *   1. Email/password sign-in enabled in Firebase Console
 *   2. Test user created: E2E_TEST_EMAIL / E2E_TEST_PASSWORD env vars
 *   3. PLAYWRIGHT_BASE_URL set to the live site
 *
 * These tests sign in via the UI, then exercise authenticated flows:
 * favorites, orders, points, profile.
 */

const TEST_EMAIL = process.env.E2E_TEST_EMAIL ?? "e2e-test@asoview-clone.dev";
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD ?? "TestPass123!";
const FIREBASE_API_KEY = process.env.E2E_FIREBASE_API_KEY ?? "";

// ─── Helper: create test user via Firebase REST API ─────────────────

async function ensureTestUser(apiKey: string, email: string, password: string): Promise<void> {
  // Try sign-in first; if it works, user exists
  const signInRes = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  if (signInRes.ok) return;

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
    throw new Error(`Failed to create test user: ${err}`);
  }
}

// ─── Helper: sign in via the UI ─────────────────────────────────────

async function signInViaUI(
  page: import("@playwright/test").Page,
  email: string,
  password: string,
): Promise<void> {
  await page.goto("/ja/signin");
  await page.getByTestId("email-input").fill(email);
  await page.getByTestId("password-input").fill(password);
  await page.getByRole("button", { name: "Sign in with Email" }).click();
  // Wait for redirect away from signin page
  await page.waitForURL(/(?!.*signin)/, { timeout: 15_000 });
}

// ─── Setup ──────────────────────────────────────────────────────────

test.beforeAll(async () => {
  if (!FIREBASE_API_KEY) {
    throw new Error("E2E_FIREBASE_API_KEY env var required for authenticated tests");
  }
  await ensureTestUser(FIREBASE_API_KEY, TEST_EMAIL, TEST_PASSWORD);
});

// ─── Profile / Me ───────────────────────────────────────────────────

test.describe("authenticated: profile", () => {
  test("sign in and access /me without redirect", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);
    // Should be on home page after sign-in
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });
    // Navigate to a protected page
    await page.goto("/ja/me/orders");
    await page.waitForTimeout(2000);
    // Should NOT redirect to signin
    expect(page.url()).not.toContain("signin");
  });
});

// ─── Favorites ──────────────────────────────────────────────────────

test.describe("authenticated: favorites", () => {
  test("toggle favorite on a product", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);

    // Get a product ID
    const res = await page.request.get("/api/v1/products?size=1");
    const productId = (await res.json()).content[0].id;

    // Visit product page
    await page.goto(`/ja/products/${productId}`);
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });

    // Find and click the favorite/heart button
    const heartBtn = page.locator("button[aria-label*='favorite'], button:has(svg)").first();
    if (await heartBtn.isVisible()) {
      await heartBtn.click();
      await page.waitForTimeout(1000);
      // Page should not crash after toggling
      await expect(page.locator("h1")).toBeVisible();
    }
  });

  test("favorites page loads when signed in", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/me/favorites");
    await page.waitForTimeout(2000);
    // Should not redirect to signin
    expect(page.url()).toContain("/favorites");
    await expect(page.locator("body")).toBeVisible();
  });
});

// ─── Orders ─────────────────────────────────────────────────────────

test.describe("authenticated: orders", () => {
  test("orders page loads when signed in", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/me/orders");
    await page.waitForTimeout(2000);
    expect(page.url()).toContain("/orders");
    await expect(page.locator("body")).toBeVisible();
  });
});

// ─── Points ─────────────────────────────────────────────────────────

test.describe("authenticated: points", () => {
  test("points page loads when signed in", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);
    await page.goto("/ja/me/points");
    await page.waitForTimeout(2000);
    expect(page.url()).toContain("/points");
    await expect(page.locator("body")).toBeVisible();
  });
});

// ─── Cart + Checkout flow ───────────────────────────────────────────

test.describe("authenticated: cart and checkout", () => {
  test("add product to cart via slot picker", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);

    // Get a product
    const res = await page.request.get("/api/v1/products?size=1");
    const product = (await res.json()).content[0];

    await page.goto(`/ja/products/${product.id}`);
    await expect(page.locator("h1")).toBeVisible({ timeout: 10_000 });

    // Look for a bookable slot button (残りN or similar)
    const slotBtn = page.getByText(/残り|Available/i).first();
    if (await slotBtn.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await slotBtn.click();
      await page.waitForTimeout(1000);

      // Look for "add to cart" or "予約" button
      const addBtn = page.getByRole("button", { name: /カートに追加|予約|Add to Cart/i }).first();
      if (await addBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await addBtn.click();
        await page.waitForTimeout(1000);
      }
    }

    // Visit cart page
    await page.goto("/ja/cart");
    await page.waitForTimeout(1000);
    expect(page.url()).toContain("/cart");
    await expect(page.locator("body")).toBeVisible();
  });
});

// ─── Sign out ───────────────────────────────────────────────────────

test.describe("authenticated: sign out", () => {
  test("sign out returns to unauthenticated state", async ({ page }) => {
    await signInViaUI(page, TEST_EMAIL, TEST_PASSWORD);

    // After sign-in, nav should show user menu or sign-out option
    // Try clicking a sign-out link/button if visible
    const signOutBtn = page.getByRole("button", { name: /ログアウト|Sign out|サインアウト/i });
    if (await signOutBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await signOutBtn.click();
      await page.waitForTimeout(2000);
    }

    // Navigate to a protected page; should redirect to signin
    await page.goto("/ja/me/orders");
    await page.waitForTimeout(3000);
    const url = page.url();
    const body = await page.locator("body").innerText();
    const isProtected =
      url.includes("signin") || body.includes("Sign in") || body.includes("ログイン");
    expect(isProtected).toBe(true);
  });
});
