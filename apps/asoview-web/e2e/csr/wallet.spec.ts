import { expect, test } from "@playwright/test";

/**
 * Wallet button visibility gating by ticket validity window. The
 * TicketCard renders AppleWalletButton + GoogleWalletButton only when
 * the current time is inside [validFrom, validUntil]. Driven via route
 * interception, no backend required.
 */

const ORDER_ID = "ord-wallet";

async function stubTicket(
  page: import("@playwright/test").Page,
  validFrom: string,
  validUntil: string,
) {
  await page.route(`**/v1/me/tickets?orderId=${ORDER_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          ticketPassId: "tp-wallet-1",
          entitlementId: "ent-wallet-1",
          orderId: ORDER_ID,
          qrCodePayload: "QR",
          status: "ACTIVE",
          validFrom,
          validUntil,
        },
      ]),
    }),
  );
}

test.describe("wallet button visibility", () => {
  test("inside validity window shows wallet buttons", async ({ page }) => {
    const past = new Date(Date.now() - 86_400_000).toISOString();
    const future = new Date(Date.now() + 86_400_000).toISOString();
    await stubTicket(page, past, future);
    await page.goto(`/ja/tickets/${ORDER_ID}`);
    await expect(page.getByRole("button", { name: /Apple Wallet/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Google Wallet/i })).toBeVisible();
  });

  test("past validity hides wallet buttons", async ({ page }) => {
    const farPast = new Date(Date.now() - 4 * 86_400_000).toISOString();
    const past = new Date(Date.now() - 3 * 86_400_000).toISOString();
    await stubTicket(page, farPast, past);
    await page.goto(`/ja/tickets/${ORDER_ID}`);
    await expect(page.getByRole("button", { name: /Apple Wallet/i })).toHaveCount(0);
    await expect(page.getByRole("button", { name: /Google Wallet/i })).toHaveCount(0);
  });
});
