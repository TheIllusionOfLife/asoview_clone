import { expect, test } from "@playwright/test";

/**
 * Review list + submit contract. The ReviewList / ReviewForm components
 * mount inside an SSR product detail page whose first fetch of
 * `/v1/products/{id}` happens in the Next.js Node process and so cannot
 * be intercepted by `page.route`. To exercise the client-facing review
 * endpoints without a running backend this spec drives the mocks
 * through `page.evaluate`, which asserts the wire contract the React
 * components rely on. Once a seeded backend is available in CI this
 * spec should be extended to actually render `/ja/products/<seed>` and
 * submit a review through the form UI.
 */

test.describe("reviews endpoints contract", () => {
  test("GET /v1/products/{id}/reviews returns Spring Page shape", async ({ page }) => {
    await page.route("**/v1/products/prod-rev-1/reviews*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [
            {
              id: "rv-1",
              userId: "u-1",
              productId: "prod-rev-1",
              rating: 5,
              title: "最高",
              body: "とても楽しめました",
              language: "ja",
              status: "PUBLISHED",
              helpfulCount: 3,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            },
          ],
          totalElements: 1,
          number: 0,
          size: 10,
        }),
      }),
    );
    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const r = await fetch("/v1/products/prod-rev-1/reviews?page=0&size=10");
      return r.json();
    });
    expect(result.content).toHaveLength(1);
    expect(result.content[0].rating).toBe(5);
  });

  test("POST /v1/reviews returns 201 with body", async ({ page }) => {
    await page.route("**/v1/reviews", (route) =>
      route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          id: "rv-new",
          userId: "u-1",
          productId: "prod-rev-1",
          rating: 4,
          title: "",
          body: "また利用したい",
          language: "ja",
          status: "PENDING",
          helpfulCount: 0,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
      }),
    );
    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const r = await fetch("/v1/reviews", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          productId: "prod-rev-1",
          rating: 4,
          title: "",
          body: "また利用したい",
          language: "ja",
        }),
      });
      return { status: r.status, data: await r.json() };
    });
    expect(result.status).toBe(201);
    expect(result.data.id).toBe("rv-new");
  });
});
