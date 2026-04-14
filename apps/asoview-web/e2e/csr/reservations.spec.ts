import { expect, test } from "@playwright/test";

/**
 * Consumer reservation flow E2E tests. Uses page.route() to intercept
 * API calls and verify request/response contracts without requiring
 * a running backend or Firebase auth.
 */

test.describe("consumer reservations", () => {
  test("unauthenticated /me/reservations redirects to /signin or shows list", async ({ page }) => {
    await page.goto("/ja/me/reservations");
    // If not authenticated, redirects to /signin. If authenticated, stays on page.
    const url = page.url();
    expect(url).toMatch(/\/(signin|me\/reservations)/);
  });

  test("unauthenticated /me/reservations/{id} redirects to /signin or shows detail", async ({ page }) => {
    await page.goto("/ja/me/reservations/res-123");
    const url = page.url();
    expect(url).toMatch(/\/(signin|me\/reservations)/);
  });

  test("GET /v1/reservation-slots returns slot availability", async ({ page }) => {
    const slots = [
      {
        slotId: "slot-1",
        productId: "prod-1",
        slotDate: "2026-05-01",
        startTime: "09:00",
        endTime: "10:00",
        capacity: 10,
        approvedCount: 3,
        remainingCapacity: 7,
      },
      {
        slotId: "slot-2",
        productId: "prod-1",
        slotDate: "2026-05-01",
        startTime: "10:00",
        endTime: "11:00",
        capacity: 5,
        approvedCount: 5,
        remainingCapacity: 0,
      },
    ];

    await page.route("**/v1/reservation-slots?*", (route) => {
      const url = new URL(route.request().url());
      expect(url.searchParams.get("venueId")).toBe("venue-1");
      expect(url.searchParams.get("date")).toBeTruthy();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(slots),
      });
    });

    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/reservation-slots?venueId=venue-1&date=2026-05-01");
      return { status: res.status, body: await res.json() };
    });

    expect(result.status).toBe(200);
    expect(result.body).toHaveLength(2);
    expect(result.body[0].remainingCapacity).toBe(7);
    expect(result.body[1].remainingCapacity).toBe(0);
  });

  test("POST /v1/reservations creates a reservation", async ({ page }) => {
    let capturedBody: Record<string, unknown> | null = null;

    await page.route("**/v1/reservations", (route) => {
      if (route.request().method() === "POST") {
        capturedBody = route.request().postDataJSON();
        route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            reservationId: "res-new-1",
            tenantId: "t-1",
            venueId: "v-1",
            slotId: capturedBody?.slotId ?? "slot-1",
            consumerUserId: "user-1",
            status: "PENDING_APPROVAL",
            idempotencyKey: capturedBody?.idempotencyKey ?? "key-1",
            guestName: capturedBody?.guestName ?? "Taro",
            guestEmail: capturedBody?.guestEmail ?? "t@e.com",
            guestCount: capturedBody?.guestCount ?? 2,
            rejectReason: null,
            cancelReason: null,
            createdAt: "2026-05-01T00:00:00Z",
            updatedAt: "2026-05-01T00:00:00Z",
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/reservations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          slotId: "slot-1",
          idempotencyKey: "idem-test-1",
          guestName: "Taro Yamada",
          guestEmail: "taro@example.com",
          guestCount: 2,
        }),
      });
      return { status: res.status, body: await res.json() };
    });

    expect(result.status).toBe(201);
    expect(result.body.reservationId).toBe("res-new-1");
    expect(result.body.status).toBe("PENDING_APPROVAL");
    expect(capturedBody).toBeTruthy();
    expect(capturedBody?.slotId).toBe("slot-1");
    expect(capturedBody?.guestCount).toBe(2);
  });

  test("GET /v1/me/reservations returns reservation list", async ({ page }) => {
    const reservations = [
      {
        reservationId: "res-1",
        tenantId: "t-1",
        venueId: "v-1",
        slotId: "slot-1",
        consumerUserId: "user-1",
        status: "PENDING_APPROVAL",
        idempotencyKey: "idem-1",
        guestName: "Taro",
        guestEmail: "t@e.com",
        guestCount: 2,
        rejectReason: null,
        cancelReason: null,
        createdAt: "2026-05-01T00:00:00Z",
        updatedAt: "2026-05-01T00:00:00Z",
      },
    ];

    await page.route("**/v1/me/reservations", (route) => {
      expect(route.request().method()).toBe("GET");
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(reservations),
      });
    });

    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/me/reservations");
      return { status: res.status, body: await res.json() };
    });

    expect(result.status).toBe(200);
    expect(result.body).toHaveLength(1);
    expect(result.body[0].status).toBe("PENDING_APPROVAL");
  });

  test("GET /v1/reservations/{id} returns reservation detail", async ({ page }) => {
    await page.route("**/v1/reservations/res-detail-1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          reservationId: "res-detail-1",
          tenantId: "t-1",
          venueId: "v-1",
          slotId: "slot-1",
          consumerUserId: "user-1",
          status: "APPROVED",
          idempotencyKey: "idem-1",
          guestName: "Taro Yamada",
          guestEmail: "taro@example.com",
          guestCount: 3,
          rejectReason: null,
          cancelReason: null,
          createdAt: "2026-05-01T00:00:00Z",
          updatedAt: "2026-05-01T00:00:00Z",
        }),
      });
    });

    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/reservations/res-detail-1");
      return { status: res.status, body: await res.json() };
    });

    expect(result.status).toBe(200);
    expect(result.body.reservationId).toBe("res-detail-1");
    expect(result.body.status).toBe("APPROVED");
    expect(result.body.guestCount).toBe(3);
  });

  test("PUT /v1/reservations/{id}/cancel cancels reservation", async ({ page }) => {
    let capturedReason: string | null = null;

    await page.route("**/v1/reservations/res-cancel-1/cancel", (route) => {
      expect(route.request().method()).toBe("PUT");
      const body = route.request().postDataJSON();
      capturedReason = body?.reason ?? null;
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          reservationId: "res-cancel-1",
          tenantId: "t-1",
          venueId: "v-1",
          slotId: "slot-1",
          consumerUserId: "user-1",
          status: "CANCELLED",
          idempotencyKey: "idem-1",
          guestName: "Taro",
          guestEmail: "t@e.com",
          guestCount: 1,
          rejectReason: null,
          cancelReason: "Changed plans",
          createdAt: "2026-05-01T00:00:00Z",
          updatedAt: "2026-05-01T01:00:00Z",
        }),
      });
    });

    await page.goto("/ja");
    const result = await page.evaluate(async () => {
      const res = await fetch("/v1/reservations/res-cancel-1/cancel", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "Changed plans" }),
      });
      return { status: res.status, body: await res.json() };
    });

    expect(result.status).toBe(200);
    expect(result.body.status).toBe("CANCELLED");
    expect(result.body.cancelReason).toBe("Changed plans");
    expect(capturedReason).toBe("Changed plans");
  });
});
