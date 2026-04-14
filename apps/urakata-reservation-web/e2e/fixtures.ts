import { type Page, test as base } from "@playwright/test";

/**
 * Bypass Firebase auth by intercepting the Firebase Auth REST API.
 * The Firebase SDK makes REST calls to identitytoolkit.googleapis.com.
 * We mock those endpoints so onIdTokenChanged fires with a fake user.
 *
 * Alternative approach: inject a mock auth state via page.addInitScript
 * that overrides the Firebase SDK before the app code runs.
 */
async function mockFirebaseAuth(page: Page) {
  // Mock Firebase Auth REST API - the SDK calls these on initialization
  await page.route("**/identitytoolkit.googleapis.com/**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ users: [] }),
    }),
  );

  // Mock Firebase config fetch
  await page.route("**/__/firebase/**", (route) => route.fulfill({ status: 200, body: "{}" }));
}

/**
 * Inject a script that stubs the AuthProvider's ready/user state
 * by setting window.__MOCK_AUTH before the app hydrates.
 */
async function injectMockAuth(page: Page) {
  await page.addInitScript(() => {
    // Signal to the auth provider that we have a mock user
    (window as unknown as Record<string, unknown>).__MOCK_AUTH = {
      uid: "test-admin",
      email: "admin@test.com",
      getIdToken: () => Promise.resolve("mock-token"),
    };
  });
}

export { mockFirebaseAuth, injectMockAuth };

// Create a test fixture with mocked APIs
export const test = base.extend<{ authedPage: Page }>({
  authedPage: async ({ page }, use) => {
    await mockFirebaseAuth(page);
    await use(page);
  },
});
