import { expect, test } from "@playwright/test";

/**
 * End-to-end i18n routing: visiting `/en` must render English strings.
 * This is the only test that proves next-intl's locale prefix + message
 * loading pipeline works all the way through the rendered HTML.
 */

test.describe("locale routing", () => {
  test("/en renders English header strings", async ({ page }) => {
    await page.goto("/en");
    // "Sign in" is the English common.signIn value; the Japanese
    // equivalent ログイン would appear on /ja.
    await expect(page.getByRole("link", { name: "Sign in" }).first()).toBeVisible();
    await expect(page.getByText("ログイン")).toHaveCount(0);
  });
});
