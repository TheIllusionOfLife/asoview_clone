import { describe, expect, it } from "vitest";
import { buildCsp } from "../csp";

describe("buildCsp", () => {
  const baseEnv = {
    NEXT_PUBLIC_API_BASE_URL: "https://api.example.com",
  } as NodeJS.ProcessEnv;

  it("includes 'unsafe-eval' in script-src for development", () => {
    const csp = buildCsp({ ...baseEnv, NODE_ENV: "development" });
    expect(csp).toMatch(/script-src [^;]*'unsafe-eval'/);
  });

  it("includes 'unsafe-eval' in script-src for test", () => {
    const csp = buildCsp({ ...baseEnv, NODE_ENV: "test" });
    expect(csp).toMatch(/script-src [^;]*'unsafe-eval'/);
  });

  it("excludes 'unsafe-eval' from script-src in production", () => {
    const csp = buildCsp({ ...baseEnv, NODE_ENV: "production" });
    const scriptSrc = csp.split(";").find((d) => d.trim().startsWith("script-src")) ?? "";
    expect(scriptSrc).not.toContain("'unsafe-eval'");
  });

  it("keeps Stripe in script-src across all environments", () => {
    for (const NODE_ENV of ["development", "test", "production"] as const) {
      const csp = buildCsp({ ...baseEnv, NODE_ENV });
      expect(csp).toContain("https://js.stripe.com");
    }
  });
});
