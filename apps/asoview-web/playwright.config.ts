import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  // E2E specs are split into:
  //   - e2e/csr/: client-rendered pages or pages where SSR is bypassed by
  //     query params; specs here may use page.route(...) to mock the API.
  //   - e2e/ssr/: pages whose initial HTML is rendered server-side via a
  //     Node-side fetch — page.route() CAN'T intercept those, so specs
  //     here must NOT call page.route() and instead drive real traffic.
  //     Enforced by scripts/checks/ssr-no-route.sh (Pitfall 15).
  testDir: "./e2e",
  testMatch: ["csr/**/*.spec.ts", "ssr/**/*.spec.ts"],
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
});
