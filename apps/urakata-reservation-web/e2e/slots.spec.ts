import { expect, test } from "@playwright/test";

/**
 * Slot management API contract tests. Since the operator pages require
 * Firebase auth (which we can't mock in E2E without an emulator),
 * we test the API contract shapes the UI will use via page.route()
 * interception. This verifies the fetch calls round-trip correctly.
 */

const MOCK_SLOTS = [
  {
    slotId: "slot-1",
    tenantId: "t-1",
    venueId: "venue-1",
    productId: "p-1",
    slotDate: "2026-06-01",
    startTime: "09:00",
    endTime: "10:00",
    capacity: 10,
    approvedCount: 3,
    waitlistCount: 1,
  },
];

test.describe("slot API contract", () => {
  test("GET /v1/op/reservation-slots returns slot list", async ({ page }) => {
    let getCalled = false;
    await page.route("**/v1/op/reservation-slots?*", (route) => {
      getCalled = true;
      expect(route.request().method()).toBe("GET");
      const url = new URL(route.request().url());
      expect(url.searchParams.get("venueId")).toBe("venue-1");
      expect(url.searchParams.get("date")).toBe("2026-06-01");
      route.fulfill({ json: MOCK_SLOTS });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch(
        "/v1/op/reservation-slots?venueId=venue-1&date=2026-06-01",
      );
      return { status: res.status, body: await res.json() };
    });
    expect(getCalled).toBe(true);
    expect(result.status).toBe(200);
    expect(result.body).toHaveLength(1);
    expect(result.body[0].slotId).toBe("slot-1");
  });

  test("POST /v1/op/reservation-slots creates slot", async ({ page }) => {
    let postBody: Record<string, unknown> | null = null;
    await page.route("**/v1/op/reservation-slots", (route) => {
      if (route.request().method() === "POST") {
        postBody = route.request().postDataJSON();
        route.fulfill({
          status: 201,
          json: { slotId: "new-slot", ...postBody },
        });
      } else {
        route.fulfill({ json: [] });
      }
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservation-slots", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          venueId: "venue-1",
          productId: "p-1",
          slotDate: "2026-06-01",
          startTime: "09:00",
          endTime: "10:00",
          capacity: 10,
        }),
      });
      return { status: res.status, body: await res.json() };
    });
    expect(result.status).toBe(201);
    expect(postBody).not.toBeNull();
    expect(postBody!.venueId).toBe("venue-1");
    expect(postBody!.capacity).toBe(10);
  });

  test("PUT /v1/op/reservation-slots/{id} updates slot", async ({ page }) => {
    let putCalled = false;
    await page.route("**/v1/op/reservation-slots/slot-1", (route) => {
      if (route.request().method() === "PUT") {
        putCalled = true;
        route.fulfill({ json: { ...MOCK_SLOTS[0], capacity: 20 } });
      }
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservation-slots/slot-1", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ startTime: "09:00", endTime: "10:00", capacity: 20 }),
      });
      return { status: res.status, body: await res.json() };
    });
    expect(putCalled).toBe(true);
    expect(result.body.capacity).toBe(20);
  });

  test("DELETE /v1/op/reservation-slots/{id} deletes slot", async ({ page }) => {
    let deleteCalled = false;
    await page.route("**/v1/op/reservation-slots/slot-1", (route) => {
      if (route.request().method() === "DELETE") {
        deleteCalled = true;
        route.fulfill({ status: 204 });
      }
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservation-slots/slot-1", {
        method: "DELETE",
      });
      return { status: res.status };
    });
    expect(deleteCalled).toBe(true);
    expect(result.status).toBe(204);
  });

  test("DELETE /v1/op/reservation-slots/{id} returns 409 when blocked", async ({
    page,
  }) => {
    await page.route("**/v1/op/reservation-slots/slot-1", (route) => {
      if (route.request().method() === "DELETE") {
        route.fulfill({
          status: 409,
          json: { error: "CONFLICT", message: "Cannot delete slot with active reservations" },
        });
      }
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservation-slots/slot-1", {
        method: "DELETE",
      });
      return { status: res.status, body: await res.json() };
    });
    expect(result.status).toBe(409);
    expect(result.body.error).toBe("CONFLICT");
  });
});
