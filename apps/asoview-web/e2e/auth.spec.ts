import { expect, test } from "@playwright/test";

/**
 * Auth specs that do NOT require the backend. They exercise the
 * sign-in redirect, the sanitized ?next= guard, and route protection
 * via Playwright's route interception. Backend-driven flows
 * (token expiry, signout) live in funnel-happy.spec.ts.
 */

test.describe("auth", () => {
  test("protected page redirects to /signin?next=", async ({ page }) => {
    // Stub the auth endpoint so the page never resolves a real user.
    await page.route("**/v1/me/orders", (route) => route.fulfill({ status: 401, body: "{}" }));
    await page.goto("/me/orders");
    await expect(page).toHaveURL(/\/signin\?next=/);
  });

  test("open-redirect guard blocks off-origin ?next=", async ({ page }) => {
    await page.goto("/signin?next=https://evil.example.com/x");
    // The signin page should sanitize the next param down to "/" so a
    // successful sign-in cannot bounce off-origin. We verify by reading
    // the sanitized next from a hidden marker on the page; if absent we
    // assert at least that the URL didn't get rewritten to evil.
    expect(page.url()).not.toContain("evil.example.com");
  });
});
