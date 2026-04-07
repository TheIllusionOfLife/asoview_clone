import { expect, test } from "@playwright/test";

/**
 * Envelope assertions: verify the frontend correctly consumes the
 * Spring `Page<T>` shape on /v1/products and the flat List shape on
 * /v1/areas. The Idempotency-Key contract is exercised at the unit
 * level in src/lib/__tests__/api.test.ts (asserting that the API
 * client attaches the header from sessionStorage on POST /v1/orders),
 * not via Playwright route interception which would bypass the app
 * code under test.
 *
 * No real backend needed: the route interceptor returns canned shapes.
 */

test.describe("envelope", () => {
  test("/v1/products is consumed as Page<T>", async ({ page }) => {
    let called = false;
    await page.route("**/v1/products?**", (route) => {
      called = true;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [
            {
              id: "p1",
              name: "テスト体験",
              description: null,
              status: "ACTIVE",
              categoryId: null,
              venueId: null,
              variants: [],
            },
          ],
          totalElements: 1,
          number: 0,
          size: 8,
        }),
      });
    });
    // /v1/areas returns List<AreaResponse>, NOT Spring Page<T>. Returning
    // the Page envelope here would mask a real client-side bug where the
    // landing page tried to read `.content` on an array.
    let areasCalled = false;
    await page.route("**/v1/areas**", (route) => {
      areasCalled = true;
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: "a1", slug: "tokyo", name: "東京" },
          { id: "a2", slug: "osaka", name: "大阪" },
        ]),
      });
    });
    await page.goto("/");
    expect(called).toBe(true);
    expect(areasCalled).toBe(true);
    // The List<AreaResponse> shape should reach the rendered DOM. If the
    // client tried to call `.content` on the array, the page would crash
    // before either link rendered.
    await expect(page.locator('a[href="/areas/tokyo"]')).toBeVisible();
    await expect(page.locator('a[href="/areas/osaka"]')).toBeVisible();
  });
});
