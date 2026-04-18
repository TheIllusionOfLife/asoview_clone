import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  // smoke/ holds live-cluster audits keyed on PLAYWRIGHT_BASE_URL + API
  // secrets. Excluded from the default run so `bunx playwright test`
  // (local dev + CI without those secrets) stays green. The audit config
  // (playwright.audit.config.ts) opts smoke/ in explicitly via testMatch.
  testIgnore: ["**/smoke/**"],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "bun run dev",
    port: 3000,
    reuseExistingServer: !process.env.CI,
    env: {
      NEXT_PUBLIC_API_BASE_URL: "http://localhost:8083",
      NEXT_PUBLIC_FIREBASE_API_KEY: "test-key",
      NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN: "test.firebaseapp.com",
      NEXT_PUBLIC_FIREBASE_PROJECT_ID: "test-project",
      NEXT_PUBLIC_FIREBASE_APP_ID: "1:test:web:test",
    },
  },
});
