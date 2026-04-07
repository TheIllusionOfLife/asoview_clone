// Broken fixture: page.route() inside a spec under e2e/ssr/. The mock will
// silently never fire because Next.js fetches in Node, not the browser.
import { test, expect } from "@playwright/test";

test("ssr page with route mock (broken)", async ({ page }) => {
  await page.route("**/v1/products", (r) => r.fulfill({ status: 200, body: "[]" }));
  await page.goto("/some-ssr-page");
  await expect(page.locator("h1")).toBeVisible();
});
