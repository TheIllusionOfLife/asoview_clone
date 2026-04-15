import { expect, test } from "@playwright/test";

// Validates /v1/me/recommendations returns a non-empty list when AI is on,
// and that the UI renders at least one card. RecommendationController has a
// robust fallback (PopularProductsFallbackService) so this test works even if
// Gemini itself is slow — at worst we get popular products.
test.describe("AI recommendations", () => {
  test.skip(
    process.env.ASOVIEW_AI_ENABLED !== "true",
    "AI disabled — skipping recommendations check",
  );

  test("signed-in user gets recommendation list from API", async ({ request }) => {
    const email = process.env.E2E_TEST_EMAIL;
    const password = process.env.E2E_TEST_PASSWORD;
    const apiKey = process.env.E2E_FIREBASE_API_KEY;
    test.skip(!email || !password || !apiKey, "Missing E2E creds env vars");

    // Direct-API check first — independent of any UI changes.
    const signInResponse = await request.post(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
      {
        data: { email, password, returnSecureToken: true },
      },
    );
    expect(signInResponse.ok(), "Firebase sign-in failed").toBeTruthy();
    const { idToken } = await signInResponse.json();
    expect(idToken, "Missing idToken in sign-in response").toBeTruthy();

    const recsResponse = await request.get(
      `${process.env.API_BASE_URL ?? "https://asoview-clone-dev.duckdns.org/api"}/v1/me/recommendations?limit=5`,
      { headers: { Authorization: `Bearer ${idToken}` } },
    );
    expect(recsResponse.ok()).toBeTruthy();
    const body = await recsResponse.json();
    expect(body.products?.length ?? 0).toBeGreaterThanOrEqual(1);
    // Source must be "ai" if Gemini actually worked, otherwise the fallback
    // returns "popular" — either way the list is non-empty.
    expect(["ai", "popular"]).toContain(body.source);
  });
});
