import { expect, test } from "@playwright/test";

/**
 * Validity-window gating on /tickets/[orderId]. The TicketCard renders
 * one of three UIs based on now vs validFrom/validUntil:
 *   - before validFrom: "available from {Tokyo time}", no QR
 *   - inside: QR rendered (img alt = "QR code for ticket ...")
 *   - after validUntil: "expired", no QR
 *
 * Driven via route interception; no backend required.
 */

const ORDER_ID = "ord-validity";

async function stubTickets(
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
          ticketPassId: "tp-1",
          entitlementId: "ent-1",
          orderId: ORDER_ID,
          qrCodePayload: "QR-PAYLOAD",
          status: "ACTIVE",
          validFrom,
          validUntil,
        },
      ]),
    }),
  );
}

test.describe("ticket validity", () => {
  test("before validFrom shows 'available from' and no QR", async ({ page }) => {
    const future = new Date(Date.now() + 3 * 86_400_000).toISOString();
    const farFuture = new Date(Date.now() + 4 * 86_400_000).toISOString();
    await stubTickets(page, future, farFuture);
    await page.goto(`/ja/tickets/${ORDER_ID}`);
    // Multiple "から利用可能" labels exist on the page now (TicketCard
    // header + Apple/Google wallet button captions). Any visible one
    // proves the "before" phase is rendered.
    await expect(page.getByText(/から利用可能/).first()).toBeVisible();
    await expect(page.locator('img[alt^="QR code"]')).toHaveCount(0);
  });

  test("inside validity window renders QR", async ({ page }) => {
    const past = new Date(Date.now() - 86_400_000).toISOString();
    const future = new Date(Date.now() + 86_400_000).toISOString();
    await stubTickets(page, past, future);
    await page.goto(`/ja/tickets/${ORDER_ID}`);
    await expect(page.locator('img[alt^="QR code"]')).toBeVisible();
  });

  test("after validUntil shows 'expired' and no QR", async ({ page }) => {
    const farPast = new Date(Date.now() - 4 * 86_400_000).toISOString();
    const past = new Date(Date.now() - 3 * 86_400_000).toISOString();
    await stubTickets(page, farPast, past);
    await page.goto(`/ja/tickets/${ORDER_ID}`);
    await expect(page.getByText("期限切れ")).toBeVisible();
    await expect(page.locator('img[alt^="QR code"]')).toHaveCount(0);
  });
});
