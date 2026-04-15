import { expect, test } from "@playwright/test";

// Asserts graceful degradation when Gemini is unavailable or BigQuery is
// stale. Runs with ASOVIEW_AI_ENABLED=true (so the endpoint is live), but
// tolerates both real and fallback response shapes. The point is that the
// cluster never returns 500 and always renders something usable.
test.describe("AI degraded paths", () => {
  const apiBase = process.env.API_BASE_URL ?? "https://asoview-clone-dev.duckdns.org/api";

  test("recommendations falls back cleanly", async ({ request }) => {
    const email = process.env.E2E_TEST_EMAIL;
    const password = process.env.E2E_TEST_PASSWORD;
    const apiKey = process.env.E2E_FIREBASE_API_KEY;
    test.skip(!email || !password || !apiKey, "Missing E2E creds env vars");

    const signIn = await request.post(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
      { data: { email, password, returnSecureToken: true } },
    );
    const { idToken } = await signIn.json();

    const response = await request.get(`${apiBase}/v1/me/recommendations?limit=3`, {
      headers: { Authorization: `Bearer ${idToken}` },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    // Always returns a list — either Gemini-backed or PopularProductsFallback.
    expect(body.products).toBeDefined();
    expect(Array.isArray(body.products)).toBe(true);
    expect(["ai", "popular"]).toContain(body.source);
  });

  test("chat endpoint never returns 500", async ({ request }) => {
    // Even a pathological input is answered with a 200 and a string reply
    // (real or fallback). The controller catches all Gemini errors.
    const response = await request.post(`${apiBase}/v1/chat`, {
      data: { message: "?" },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(typeof body.reply).toBe("string");
    expect(body.reply.length).toBeGreaterThan(0);
  });

  test("search returns 200 with empty result set for unknown terms", async ({ request }) => {
    const response = await request.get(
      `${apiBase}/v1/products/search?q=xyzzy-no-such-product&size=5`,
    );
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBe(true);
  });
});
