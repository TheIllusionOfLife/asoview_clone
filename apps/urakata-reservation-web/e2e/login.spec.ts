import { expect, test } from "@playwright/test";

test.describe("login page", () => {
  test("unauthenticated visit shows login form", async ({ page }) => {
    await page.goto("/ja/login");
    await expect(page.locator("h1")).toContainText("管理画面ログイン");
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test("login form has required fields", async ({ page }) => {
    await page.goto("/ja/login");
    const emailInput = page.locator("#email");
    const passwordInput = page.locator("#password");
    await expect(emailInput).toHaveAttribute("required", "");
    await expect(passwordInput).toHaveAttribute("required", "");
  });

  test("en locale shows English login", async ({ page }) => {
    await page.goto("/en/login");
    await expect(page.locator("h1")).toContainText("Admin Login");
    await expect(page.locator('button[type="submit"]')).toContainText("Sign In");
  });
});
