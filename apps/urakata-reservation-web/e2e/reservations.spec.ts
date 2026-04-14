import { expect, test } from "@playwright/test";

/**
 * Reservation API contract tests. Tests the API shapes the UI consumes
 * via page.route() interception on the login page (which renders without auth).
 */

const MOCK_RESERVATION = {
  reservationId: "res-1",
  tenantId: "t-1",
  venueId: "venue-1",
  slotId: "slot-1",
  consumerUserId: "user-1",
  status: "PENDING_APPROVAL",
  idempotencyKey: "idem-1",
  guestName: "Taro Yamada",
  guestEmail: "taro@example.com",
  guestCount: 2,
  rejectReason: null,
  cancelReason: null,
  createdAt: "2026-06-01T09:00:00Z",
  updatedAt: "2026-06-01T09:00:00Z",
};

test.describe("reservation API contract", () => {
  test("GET /v1/op/reservations returns list (with status)", async ({ page }) => {
    let getCalled = false;
    await page.route("**/v1/op/reservations?*", (route) => {
      getCalled = true;
      const url = new URL(route.request().url());
      expect(url.searchParams.get("venueId")).toBe("venue-1");
      expect(url.searchParams.get("status")).toBe("PENDING_APPROVAL");
      route.fulfill({ json: [MOCK_RESERVATION] });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations?venueId=venue-1&status=PENDING_APPROVAL");
      return { status: res.status, body: await res.json() };
    });
    expect(getCalled).toBe(true);
    expect(result.body).toHaveLength(1);
    expect(result.body[0].guestName).toBe("Taro Yamada");
  });

  test("GET /v1/op/reservations returns list (without status = ALL)", async ({ page }) => {
    let url: URL | null = null;
    await page.route("**/v1/op/reservations?*", (route) => {
      url = new URL(route.request().url());
      route.fulfill({ json: [MOCK_RESERVATION] });
    });
    await page.goto("/ja/login");
    await page.evaluate(async () => {
      await fetch("/v1/op/reservations?venueId=venue-1");
    });
    expect(url).not.toBeNull();
    expect(url?.searchParams.get("status")).toBeNull();
  });

  test("PUT /v1/op/reservations/{id}/approve transitions status", async ({ page }) => {
    let approveCalled = false;
    await page.route("**/v1/op/reservations/res-1/approve", (route) => {
      approveCalled = true;
      route.fulfill({
        json: { ...MOCK_RESERVATION, status: "APPROVED" },
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations/res-1/approve", {
        method: "PUT",
      });
      return { status: res.status, body: await res.json() };
    });
    expect(approveCalled).toBe(true);
    expect(result.body.status).toBe("APPROVED");
  });

  test("PUT /v1/op/reservations/{id}/reject sends reason", async ({ page }) => {
    let body: Record<string, unknown> | null = null;
    await page.route("**/v1/op/reservations/res-1/reject", (route) => {
      body = route.request().postDataJSON();
      route.fulfill({
        json: { ...MOCK_RESERVATION, status: "REJECTED", rejectReason: "No" },
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations/res-1/reject", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "No" }),
      });
      return { status: res.status, body: await res.json() };
    });
    expect(body).not.toBeNull();
    expect(body?.reason).toBe("No");
    expect(result.body.status).toBe("REJECTED");
  });

  test("PUT /v1/op/reservations/{id}/waitlist transitions status", async ({ page }) => {
    await page.route("**/v1/op/reservations/res-1/waitlist", (route) => {
      route.fulfill({
        json: { ...MOCK_RESERVATION, status: "WAITLISTED" },
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations/res-1/waitlist", {
        method: "PUT",
      });
      return { body: await res.json() };
    });
    expect(result.body.status).toBe("WAITLISTED");
  });

  test("PUT /v1/op/reservations/{id}/cancel sends reason", async ({ page }) => {
    let body: Record<string, unknown> | null = null;
    await page.route("**/v1/op/reservations/res-1/cancel", (route) => {
      body = route.request().postDataJSON();
      route.fulfill({
        json: { ...MOCK_RESERVATION, status: "CANCELLED", cancelReason: "Done" },
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations/res-1/cancel", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "Done" }),
      });
      return { body: await res.json() };
    });
    expect(body?.reason).toBe("Done");
    expect(result.body.status).toBe("CANCELLED");
  });

  test("GET /v1/op/reservations/{id}/audit returns audit log", async ({ page }) => {
    await page.route("**/v1/op/reservations/res-1/audit", (route) => {
      route.fulfill({
        json: [
          {
            logId: "log-1",
            reservationId: "res-1",
            action: "CREATED",
            actorUserId: "user-1",
            reason: null,
            createdAt: "2026-06-01T09:00:00Z",
          },
          {
            logId: "log-2",
            reservationId: "res-1",
            action: "APPROVED",
            actorUserId: null,
            reason: null,
            createdAt: "2026-06-01T10:00:00Z",
          },
        ],
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/reservations/res-1/audit");
      return { body: await res.json() };
    });
    expect(result.body).toHaveLength(2);
    expect(result.body[0].action).toBe("CREATED");
    expect(result.body[1].action).toBe("APPROVED");
  });

  test("GET /v1/op/dashboard returns summary", async ({ page }) => {
    await page.route("**/v1/op/dashboard?*", (route) => {
      route.fulfill({
        json: {
          reservationCounts: { PENDING_APPROVAL: 3, APPROVED: 5, WAITLISTED: 1 },
          slotUtilization: { totalSlots: 10, totalCapacity: 100, totalApproved: 50 },
        },
      });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/dashboard?venueId=venue-1");
      return { body: await res.json() };
    });
    expect(result.body.reservationCounts.PENDING_APPROVAL).toBe(3);
    expect(result.body.slotUtilization.totalSlots).toBe(10);
  });

  test("GET /v1/op/me/venues returns venue list", async ({ page }) => {
    await page.route("**/v1/op/me/venues", (route) => {
      route.fulfill({ json: ["venue-1", "venue-2", "venue-3"] });
    });
    await page.goto("/ja/login");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/op/me/venues");
      return { body: await res.json() };
    });
    expect(result.body).toEqual(["venue-1", "venue-2", "venue-3"]);
  });
});
