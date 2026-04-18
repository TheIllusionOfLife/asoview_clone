import { defineConfig } from "@playwright/test";
import base from "./playwright.config";

// Live-cluster audit variant. Mirrors asoview-web and urakata-ticket-web:
// skip the local webServer, match only smoke/service-audit, run serial with
// no retries so failures surface directly in the dev-audit.yml workflow.
export default defineConfig({
  ...base,
  testMatch: ["smoke/service-audit.spec.ts"],
  webServer: undefined,
  retries: 0,
  fullyParallel: false,
  workers: 1,
  reporter: [["list", { printSteps: true }]],
});
