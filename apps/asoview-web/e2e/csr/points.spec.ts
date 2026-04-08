import { expect, test } from "@playwright/test";

/**
 * Points balance + "use points" UI on `/ja/cart`. The points input is
 * only rendered when `useAuth().user` is truthy, which requires a real
 * Firebase session. Without auth the cart still renders the subtotal,
 * so this spec:
 *   1. Pre-seeds the guest cart in localStorage before navigation.
 *   2. Stubs `/v1/me/points` to return 500 (so the clamp upper bound
 *      is exercised when a signed-in build hits this page).
 *   3. Asserts the subtotal renders against the seeded line.
 *
 * Full "enter 300 points, assert total decreases" coverage requires a
 * signed-in build and is deferred to the CI job with Firebase emulator.
 */

test.describe("cart + points", () => {
  test("guest cart renders subtotal from pre-seeded line", async ({ page }) => {
    await page.route("**/v1/me/points", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ balance: 500 }),
      }),
    );
    await page.addInitScript(() => {
      const cart = {
        lines: [
          {
            productId: "prod-points-1",
            productVariantId: "var-1",
            slotId: "slot-1",
            slotStartAt: "2030-01-01T10:00:00",
            slotEndAt: "2030-01-01T11:00:00",
            quantity: 2,
            unitPrice: "1500.00",
            productSnapshot: { name: "テストプラン", area: "東京" },
          },
        ],
      };
      localStorage.setItem("asoview:cart:guest", JSON.stringify(cart));
    });
    await page.goto("/ja/cart");
    await expect(page.getByRole("heading", { name: "カート" })).toBeVisible();
    await expect(page.getByText("テストプラン")).toBeVisible();
    // 1500 * 2 = 3000 yen subtotal.
    await expect(page.getByText(/￥3,000|¥3,000/)).toBeVisible();
  });
});
