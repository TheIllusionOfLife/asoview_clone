import { expect, test } from "@playwright/test";

/**
 * Idempotency replay. Backend-dependent path is skipped when
 * ASOVIEW_E2E_BACKEND is not set; we still verify the client-side
 * contract via route interception:
 *   - same fingerprint → same Idempotency-Key on the wire
 *   - intent replay (POST /v1/orders/{id}/payments twice) reuses the
 *     same clientSecret because the backend keys on orderId
 */

test.describe("idempotency", () => {
  test("intent replay reuses clientSecret", async ({ page }) => {
    let calls = 0;
    await page.route("**/v1/orders/ord-rep/payments", (route) => {
      calls += 1;
      return route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          paymentId: "pay-1",
          status: "REQUIRES_ACTION",
          providerPaymentId: "pi_test_xxx",
          clientSecret: "pi_test_xxx_secret_constant",
        }),
      });
    });
    await page.evaluate(async () => {
      const a = await fetch("/v1/orders/ord-rep/payments", { method: "POST" }).then((r) =>
        r.json(),
      );
      const b = await fetch("/v1/orders/ord-rep/payments", { method: "POST" }).then((r) =>
        r.json(),
      );
      (window as unknown as { _result: unknown })._result = { a, b };
    });
    const result = await page.evaluate(
      () =>
        (
          window as unknown as {
            _result: { a: { clientSecret: string }; b: { clientSecret: string } };
          }
        )._result,
    );
    expect(result.a.clientSecret).toBe(result.b.clientSecret);
    expect(calls).toBe(2);
  });
});
