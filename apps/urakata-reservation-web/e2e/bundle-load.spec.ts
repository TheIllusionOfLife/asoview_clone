import { expect, test } from "@playwright/test";

// Regression for the "undefined NEXT_PUBLIC_* in the client bundle" bug: if the Cloud Build
// step forgets --build-arg, firebase.ts readConfig() throws on first page load and React never
// hydrates. Same test lives in urakata-ticket-web/e2e; both must stay green.
test.describe("bundle load", () => {
  test("login page hydrates without Firebase config errors", async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on("pageerror", (err) => consoleErrors.push(err.message));
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });

    await page.goto("/ja/login", { waitUntil: "domcontentloaded" });
    await expect(page.locator("h1")).toContainText("管理画面ログイン");
    await expect(page.locator('input[type="email"]')).toBeVisible();

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
