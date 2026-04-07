import { expect, test } from "@playwright/test";

/**
 * Cross-user 404. The backend masks foreign-order existence as 404 on
 * GET /v1/orders/{id} (CheckoutClient surfaces this) and as 200 [] on
 * GET /v1/me/tickets?orderId= (TicketsClient renders an empty list as
 * "not found" so existence does not leak).
 */

const FOREIGN_ORDER_ID = "ord-belongs-to-someone-else";

test.describe("cross-user", () => {
  test("/checkout/[orderId] foreign order surfaces 'not found'", async ({ page }) => {
    await page.route(`**/v1/orders/${FOREIGN_ORDER_ID}`, (route) =>
      route.fulfill({ status: 404, body: "{}" }),
    );
    await page.goto(`/checkout/${FOREIGN_ORDER_ID}`);
    await expect(page.getByText("この注文は見つかりませんでした。")).toBeVisible();
  });

  test("/tickets/[orderId] foreign order surfaces 'not found'", async ({ page }) => {
    await page.route(`**/v1/me/tickets?orderId=${FOREIGN_ORDER_ID}`, (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
      }),
    );
    await page.goto(`/tickets/${FOREIGN_ORDER_ID}`);
    await expect(page.getByText("このチケットは見つかりませんでした。")).toBeVisible();
  });
});
