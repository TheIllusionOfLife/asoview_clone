import { defineConfig } from "@playwright/test";
import base from "./playwright.config";

export default defineConfig({
  ...base,
  testMatch: ["smoke/service-audit.spec.ts"],
  retries: 0,
  fullyParallel: false,
  workers: 1,
  reporter: [["list", { printSteps: true }]],
});
