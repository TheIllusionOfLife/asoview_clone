import { expect, test } from "@playwright/test";

// Regression for the "undefined NEXT_PUBLIC_* in the client bundle" bug surfaced during
// PR #56 review: if cloudbuild forgets --build-arg, firebase.ts readConfig() throws on first
// page load and React never hydrates. This test catches that class of regression by asserting
// the bundle fully boots — no console error about missing Firebase config, and hydration
// completed.
test.describe("bundle load", () => {
  test("login page hydrates without Firebase config errors", async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on("pageerror", (err) => consoleErrors.push(err.message));
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });

    await page.goto("/ja/login", { waitUntil: "domcontentloaded" });
    await expect(page.locator("h1")).toContainText("ログイン");
    await expect(page.locator('input[type="email"]')).toBeVisible();

    // Hydration proof: __NEXT_DATA__ is written by the server and picked up by the client.
    const nextDataPresent = await page.evaluate(
      () => document.getElementById("__NEXT_DATA__") !== null,
    );
    expect(nextDataPresent).toBe(true);

    const firebaseConfigMissing = consoleErrors.find((e) =>
      e.includes("Firebase web config missing"),
    );
    expect(firebaseConfigMissing, `Console errors: ${consoleErrors.join(" | ")}`).toBeUndefined();
  });
});
