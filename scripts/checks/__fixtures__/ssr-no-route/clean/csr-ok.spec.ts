// Clean fixture (mimics e2e/ssr/): no page.route, drives real traffic.
import { test, expect } from "@playwright/test";

test("ssr page renders something", async ({ page }) => {
  await page.goto("/some-ssr-page");
  await expect(page.locator("h1")).toBeVisible();
});
