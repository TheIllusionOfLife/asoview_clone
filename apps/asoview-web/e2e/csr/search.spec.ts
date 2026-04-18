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

// Keep these in sync with the UUIDs Facets.tsx expects from /v1/categories/active
// (see services/commerce-core/.../CategoryResponse). The values themselves are
// arbitrary fixtures — the real dev-cluster UUIDs are different.
const OUTDOOR_UUID = "ce61286b-0855-5726-b270-ef6079237eed";
const INDOOR_UUID = "fa1a1636-7474-542e-b925-7a8a6c8e50bb";

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
  // /v1/categories/active feeds the category <select>. Without this stub the
  // select stays disabled (Loading categories… placeholder) and selectOption
  // times out. The slug-based fallback was removed because Vertex indexes
  // categoryId as a UUID — slugs would silently return zero hits.
  await page.route("**/v1/categories/active**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        { id: OUTDOOR_UUID, name: "Outdoor" },
        { id: INDOOR_UUID, name: "Indoor" },
      ]),
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
    // Wait for /v1/categories/active to resolve so the select enables.
    await expect(categorySelect).toBeEnabled();
    await categorySelect.selectOption(OUTDOOR_UUID);

    await expect(page).toHaveURL(new RegExp(`\\?q=bbq&category=${OUTDOOR_UUID}`));

    await page.reload();
    await expect(categorySelect).toBeEnabled();
    await expect(categorySelect).toHaveValue(OUTDOOR_UUID);
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
