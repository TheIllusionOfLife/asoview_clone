import { defineConfig } from "@playwright/test";
import base from "./playwright.config";

// Live-cluster audit variant. Mirrors asoview-web's playwright.audit.config.ts:
// skip the webServer (no local bun run dev), match only smoke/service-audit,
// run serial with no retries so failures surface directly in the scheduled
// dev-audit.yml workflow.
export default defineConfig({
  ...base,
  // Base config sets testIgnore: ["**/smoke/**"] to keep default runs
  // green without live-audit secrets; override to empty here because
  // this config's whole purpose IS the smoke/ tree.
  testIgnore: [],
  testMatch: ["smoke/service-audit.spec.ts"],
  webServer: undefined,
  retries: 0,
  fullyParallel: false,
  workers: 1,
  reporter: [["list", { printSteps: true }]],
});
