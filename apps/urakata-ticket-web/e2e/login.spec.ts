import { expect, test } from "@playwright/test";

test.describe("login page", () => {
  test("ja locale shows login form in Japanese", async ({ page }) => {
    await page.goto("/ja/login");
    await expect(page.locator("h1")).toContainText("ログイン");
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test("required fields are required", async ({ page }) => {
    await page.goto("/ja/login");
    await expect(page.locator("#email")).toHaveAttribute("required", "");
    await expect(page.locator("#password")).toHaveAttribute("required", "");
  });

  test("en locale shows login form in English", async ({ page }) => {
    await page.goto("/en/login");
    await expect(page.locator("h1")).toContainText("Login");
    await expect(page.locator('button[type="submit"]')).toContainText("Sign In");
  });

  test("unauthenticated visit to /ja redirects to /ja/login", async ({ page }) => {
    await page.goto("/ja");
    await page.waitForURL(/\/ja\/login/, { timeout: 10_000 });
    await expect(page.locator("h1")).toContainText("ログイン");
  });
});
