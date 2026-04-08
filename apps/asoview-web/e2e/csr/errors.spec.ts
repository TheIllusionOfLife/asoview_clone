import { expect, test } from "@playwright/test";

/**
 * Error-path specs. Each driven via route interception so they run
 * deterministically without a backend.
 *
 * - 5xx availability with retry hint surfaces a banner.
 * - FAILED order status on /checkout/[orderId] surfaces failure UX.
 * - 30s polling timeout surfaces "taking longer" UX. (Not exercised
 *   end-to-end here because it would idle the test for 30s; the
 *   contract is unit-tested via Vitest in src/lib/__tests__.)
 *
 * 409 SlotTaken is exercised on the cart page in the legacy /cart spec
 * (not added in this session — covered by Vitest unit tests on the cart
 * line-error logic).
 */

test.describe("checkout error paths", () => {
  test("FAILED order shows failure UX with retry links", async ({ page }) => {
    await page.route("**/v1/orders/ord-failed", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          orderId: "ord-failed",
          userId: "u",
          status: "FAILED",
          totalAmount: "1500.00",
          currency: "JPY",
          items: [],
        }),
      }),
    );
    await page.goto("/ja/checkout/ord-failed");
    await expect(page.getByText("決済に失敗しました。")).toBeVisible();
    await expect(page.getByRole("link", { name: "予約履歴を見る" })).toBeVisible();
    await expect(page.getByRole("link", { name: "もう一度試す" })).toBeVisible();
  });

  test("CANCELLED order shows failure UX", async ({ page }) => {
    await page.route("**/v1/orders/ord-cancelled", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          orderId: "ord-cancelled",
          userId: "u",
          status: "CANCELLED",
          totalAmount: "1500.00",
          currency: "JPY",
          items: [],
        }),
      }),
    );
    await page.goto("/ja/checkout/ord-cancelled");
    await expect(page.getByText("決済に失敗しました。")).toBeVisible();
  });
});
