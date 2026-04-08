import { expect, test } from "@playwright/test";

/**
 * /ja/search URL-state roundtrip. Every facet and the query box are
 * controlled by URL params; the page re-fetches `/v1/search` on every
 * change and re-reads `/v1/search/suggest` for the autocomplete. Both
 * are CSR fetches so `page.route` interception works.
 *
 * No backend required.
 */

type Hit = { productId: string; name: string; description?: string; minPrice?: number };

async function stubSearch(page: import("@playwright/test").Page, hits: Hit[]) {
  await page.route("**/v1/search**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: hits,
        totalElements: hits.length,
        number: 0,
        size: 20,
      }),
    }),
  );
  await page.route("**/v1/search/suggest**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ suggestions: [] }),
    }),
  );
}

test.describe("search URL state", () => {
  test("query typing updates ?q= and reload preserves it", async ({ page }) => {
    await stubSearch(page, [{ productId: "p-kayak", name: "カヤック体験", minPrice: 3500 }]);
    await page.goto("/ja/search");

    const input = page.getByRole("searchbox");
    await input.fill("カヤック");
    await input.press("Enter");

    await expect(page).toHaveURL(/\?q=%E3%82%AB%E3%83%A4%E3%83%83%E3%82%AF/);
    await expect(page.getByText("カヤック体験")).toBeVisible();

    await page.reload();
    await expect(input).toHaveValue("カヤック");
  });

  test("category facet appends ?category= alongside ?q=", async ({ page }) => {
    await stubSearch(page, [{ productId: "p-bbq", name: "BBQプラン", minPrice: 2500 }]);
    await page.goto("/ja/search?q=bbq");

    const categorySelect = page.locator("select").first();
    await categorySelect.selectOption("outdoor");

    await expect(page).toHaveURL(/\?q=bbq&category=outdoor/);

    await page.reload();
    await expect(categorySelect).toHaveValue("outdoor");
  });
});

test.describe("search /en locale", () => {
  test("English locale renders English facet labels", async ({ page }) => {
    await stubSearch(page, []);
    await page.goto("/en/search");
    await expect(page.getByText("Category")).toBeVisible();
    await expect(page.getByText("Sort")).toBeVisible();
  });
});
