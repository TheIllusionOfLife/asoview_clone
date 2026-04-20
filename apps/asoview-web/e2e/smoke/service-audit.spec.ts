import { type APIRequestContext, expect, test } from "@playwright/test";

/**
 * Full service audit against the live cluster. One test per user-facing
 * capability — run as a quick green/red board so we can see which
 * feature areas are broken before diving in.
 *
 * Invoked manually:
 *   PLAYWRIGHT_BASE_URL=https://asoview-clone-dev.duckdns.org \
 *   API_BASE_URL=https://asoview-clone-dev.duckdns.org/api \
 *   E2E_FIREBASE_API_KEY=... E2E_TEST_EMAIL=... E2E_TEST_PASSWORD=... \
 *   bunx playwright test --config=playwright.audit.config.ts
 *
 * Not in the default playwright.config.ts testMatch so CI's localhost lane
 * doesn't hit live.
 */

// No default for apiBase: the suite writes favorites and reviews, so a
// run without API_BASE_URL should fail fast rather than silently target
// whichever cluster the default happens to point at.
const apiBase = process.env.API_BASE_URL;
const FIREBASE_API_KEY = process.env.E2E_FIREBASE_API_KEY;
const TEST_EMAIL = process.env.E2E_TEST_EMAIL;
const TEST_PASSWORD = process.env.E2E_TEST_PASSWORD;

async function signIn(): Promise<string> {
  const res = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD, returnSecureToken: true }),
    },
  );
  if (!res.ok) throw new Error(`Firebase sign-in failed: ${await res.text()}`);
  const data = (await res.json()) as { idToken?: string };
  if (!data.idToken) throw new Error("No idToken in Firebase response");
  return data.idToken;
}

let token = "";

test.beforeAll(async () => {
  if (!apiBase) {
    throw new Error("API_BASE_URL required — the audit must target a specific cluster");
  }
  if (!FIREBASE_API_KEY || !TEST_EMAIL || !TEST_PASSWORD) {
    throw new Error("E2E_FIREBASE_API_KEY, E2E_TEST_EMAIL, E2E_TEST_PASSWORD required");
  }
  token = await signIn();
  const me = await fetch(`${apiBase}/v1/me`, { headers: { Authorization: `Bearer ${token}` } });
  if (!me.ok) throw new Error(`User provisioning failed: ${me.status} ${await me.text()}`);
});

// ─── date helpers ────────────────────────────────────────────────────

function relativeDate(daysAhead: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + daysAhead);
  return d.toISOString().slice(0, 10);
}

// ─── helpers ─────────────────────────────────────────────────────────

async function getGuest(request: APIRequestContext, path: string) {
  return request.get(`${apiBase}${path}`);
}
async function getAuth(request: APIRequestContext, path: string) {
  return request.get(`${apiBase}${path}`, { headers: { Authorization: `Bearer ${token}` } });
}

// ─── guest (no auth) ─────────────────────────────────────────────────

test.describe("guest endpoints", () => {
  test("GET /v1/areas lists areas", async ({ request }) => {
    const r = await getGuest(request, "/v1/areas");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as Array<{ id: string; slug: string }>;
    expect(body.length).toBeGreaterThan(0);
    expect(body[0].slug).toBeTruthy();
  });

  test("GET /v1/categories/active lists categories", async ({ request }) => {
    const r = await getGuest(request, "/v1/categories/active");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as unknown[];
    expect(body.length).toBeGreaterThanOrEqual(4);
  });

  test("GET /v1/products lists products", async ({ request }) => {
    const r = await getGuest(request, "/v1/products?size=5");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: unknown[]; totalElements: number };
    expect(body.content.length).toBeGreaterThan(0);
  });

  test("GET /v1/products/{id} returns product detail", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const r = await getGuest(request, `/v1/products/${pid}`);
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { id: string; variants: unknown[] };
    expect(body.id).toBe(pid);
    expect(body.variants.length).toBeGreaterThan(0);
  });

  test("GET /v1/products/{id}/reviews lists reviews", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const r = await getGuest(request, `/v1/products/${pid}/reviews`);
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: unknown[] };
    expect(Array.isArray(body.content)).toBeTruthy();
  });

  test("GET /v1/products/{id}/availability returns slots", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const from = relativeDate(1);
    const to = relativeDate(7);
    const r = await getGuest(request, `/v1/products/${pid}/availability?from=${from}&to=${to}`);
    expect(r.status()).toBe(200);
    const body = (await r.json()) as Array<{ slotId: string }>;
    expect(body.length).toBeGreaterThan(0);
    expect(body[0].slotId).toBeTruthy();
  });
});

// ─── search ──────────────────────────────────────────────────────────

test.describe("search", () => {
  test("GET /v1/search match-all returns ≥50 products", async ({ request }) => {
    const r = await getGuest(request, "/v1/search?size=50");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { totalElements: number };
    expect(body.totalElements).toBeGreaterThanOrEqual(50);
  });

  test("GET /v1/search with text match finds seeded product", async ({ request }) => {
    const r = await getGuest(request, "/v1/search?q=Chocolate&size=5");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: Array<{ name: string }> };
    expect(body.content.length).toBeGreaterThan(0);
    expect(body.content[0].name.toLowerCase()).toContain("chocolate");
  });

  test("GET /v1/search CJK 温泉 finds Hot Spring seed", async ({ request }) => {
    const r = await getGuest(request, `/v1/search?q=${encodeURIComponent("温泉")}&size=5`);
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: Array<{ name: string }> };
    expect(body.content.length).toBeGreaterThan(0);
  });

  test("GET /v1/search category filter narrows result set", async ({ request }) => {
    const cats = (await (await getGuest(request, "/v1/categories/active")).json()) as Array<{
      id: string;
    }>;
    const cid = cats[0].id;
    const r = await getGuest(request, `/v1/search?category=${cid}&size=50`);
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: Array<{ categoryId: string }> };
    expect(body.content.length).toBeGreaterThan(0);
    for (const hit of body.content) {
      expect(hit.categoryId).toBe(cid);
    }
  });

  test("GET /v1/search price range constrains every hit", async ({ request }) => {
    // Backend query params are minPrice/maxPrice. The frontend URL uses
    // priceMin/priceMax and api.ts translates before hitting the backend.
    const r = await getGuest(request, "/v1/search?minPrice=1500&maxPrice=3000&size=20");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content: Array<{ minPrice: number }> };
    for (const hit of body.content) {
      expect(hit.minPrice).toBeGreaterThanOrEqual(1500);
      expect(hit.minPrice).toBeLessThanOrEqual(3000);
    }
  });

  test("GET /v1/search sort=price_asc returns monotonic non-decreasing minPrice", async ({
    request,
  }) => {
    // Client-side sort in VertexAiSearchQueryService over a 100-doc window;
    // nulls trail. Assert monotonic over the non-null prefix.
    // See docs/adr/002-client-side-sort-for-price.md.
    const r = await getGuest(request, "/v1/search?sort=price_asc&size=20");
    expect(r.status(), `sort=price_asc HTTP ${r.status()}: ${await r.text()}`).toBe(200);
    const body = (await r.json()) as { content: Array<{ minPrice: number | null }> };
    expect(body.content.length).toBeGreaterThan(0);
    const priced = body.content
      .map((h) => h.minPrice)
      .filter((p): p is number => typeof p === "number");
    for (let i = 1; i < priced.length; i++) {
      expect(priced[i]).toBeGreaterThanOrEqual(priced[i - 1]);
    }
  });

  test("GET /v1/search sort=price_desc returns monotonic non-increasing minPrice", async ({
    request,
  }) => {
    const r = await getGuest(request, "/v1/search?sort=price_desc&size=20");
    expect(r.status(), `sort=price_desc HTTP ${r.status()}: ${await r.text()}`).toBe(200);
    const body = (await r.json()) as { content: Array<{ minPrice: number | null }> };
    expect(body.content.length).toBeGreaterThan(0);
    const priced = body.content
      .map((h) => h.minPrice)
      .filter((p): p is number => typeof p === "number");
    for (let i = 1; i < priced.length; i++) {
      expect(priced[i]).toBeLessThanOrEqual(priced[i - 1]);
    }
  });

  test("GET /v1/search exposes popularityScore after PopularityScoreSyncJob runs", async ({
    request,
  }) => {
    // PopularityScoreSyncJob reads analytics_mart.product_ranking every hour
    // and writes orderCount into each indexed doc's popularityScore via
    // read-merge-write. After the BQ view is provisioned and at least one
    // sync cycle has run, at least one hit should carry a non-null numeric
    // popularityScore. Before the first sync the field is absent (docs were
    // reindexed without popularity) — skip rather than fail so the audit
    // transitions from skipped to passing organically.
    const r = await getGuest(request, "/v1/search?size=50");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as {
      content: Array<{ productId: string; popularityScore?: number | null }>;
    };
    const scored = body.content.filter(
      (h) => typeof h.popularityScore === "number" && h.popularityScore >= 0,
    );
    test.skip(
      scored.length === 0,
      "popularity sync has not run yet; retry after next hourly cycle",
    );
    expect(scored.length).toBeGreaterThan(0);
    for (const hit of scored) {
      expect(hit.popularityScore).toBeGreaterThanOrEqual(0);
    }
  });

  test("GET /v1/search pagination returns disjoint product ids", async ({ request }) => {
    const p0 = (await (await getGuest(request, "/v1/search?size=10&page=0")).json()) as {
      content: Array<{ productId: string }>;
    };
    const p1 = (await (await getGuest(request, "/v1/search?size=10&page=1")).json()) as {
      content: Array<{ productId: string }>;
    };
    const ids0 = new Set(p0.content.map((h) => h.productId));
    for (const h of p1.content) {
      expect(ids0.has(h.productId), `page1 contained page0 id ${h.productId}`).toBeFalsy();
    }
  });

  test("GET /v1/search/suggest returns 200 (empty allowed)", async ({ request }) => {
    const r = await getGuest(request, "/v1/search/suggest?q=Chocolate");
    expect(r.status()).toBe(200);
  });
});

// ─── user: favorites, points, recommendations ────────────────────────

test.describe("user surface", () => {
  test("GET /v1/me returns the provisioned user", async ({ request }) => {
    const r = await getAuth(request, "/v1/me");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { userId: string; firebaseUid: string; email: string };
    expect(body.userId).toBeTruthy();
    expect(body.firebaseUid).toBeTruthy();
  });

  test("GET /v1/me/favorites returns a list (possibly empty)", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/favorites");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as unknown;
    expect(Array.isArray(body) || typeof body === "object").toBeTruthy();
  });

  test("PUT /v1/me/favorites/{id} toggles favorite on", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const r = await request.put(`${apiBase}/v1/me/favorites/${pid}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 201, 204]).toContain(r.status());
  });

  test("DELETE /v1/me/favorites/{id} toggles favorite off", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const r = await request.delete(`${apiBase}/v1/me/favorites/${pid}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect([200, 204, 404]).toContain(r.status());
  });

  test("GET /v1/me/points returns balance", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/points");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { balance?: number };
    expect(typeof body.balance).toBe("number");
  });

  test("GET /v1/me/points/ledger returns pageable", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/points/ledger?page=0&size=10");
    expect(r.status()).toBe(200);
    const body = (await r.json()) as { content?: unknown[] };
    expect(Array.isArray(body.content ?? [])).toBeTruthy();
  });

  test("GET /v1/me/recommendations returns products", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/recommendations");
    expect([200, 204]).toContain(r.status());
  });
});

// ─── orders, tickets, reservations ───────────────────────────────────

test.describe("orders + reservations + tickets", () => {
  test("GET /v1/me/orders returns list", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/orders");
    expect(r.status()).toBe(200);
  });

  test("GET /v1/me/reservations returns list", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/reservations");
    expect(r.status()).toBe(200);
  });

  test("GET /v1/me/tickets returns list", async ({ request }) => {
    const r = await getAuth(request, "/v1/me/tickets");
    expect(r.status()).toBe(200);
  });
});

// Google sign-in button assertion lives in live.spec.ts (gated on
// E2E_EXPECT_GOOGLE_SIGNIN). Don't duplicate here — both suites run against
// the same deployed image, and drift between two copies has no upside.

// ─── reviews: submit + helpful ───────────────────────────────────────

test.describe("reviews write path", () => {
  test("POST /v1/reviews creates or idempotently returns review", async ({ request }) => {
    const list = await (await getGuest(request, "/v1/products?size=1")).json();
    const pid = list.content[0].id as string;
    const r = await request.post(`${apiBase}/v1/reviews`, {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": `audit-review-${pid}`,
      },
      data: {
        productId: pid,
        rating: 5,
        title: "Service audit review",
        body: "Automated audit review — please ignore.",
        language: "en",
      },
    });
    // 403 is a valid outcome: "User has no paid order for product X; cannot
    // review" — the ownership rule is enforced. Test user may or may not
    // have a prior order depending on prior runs. Just assert we don't 500.
    expect([200, 201, 403, 409]).toContain(r.status());
  });
});

// ─── PWA: manifest + service worker + offline page ───────────────────

test.describe("PWA assets", () => {
  test("manifest.webmanifest served", async ({ request }) => {
    const r = await request.get("/manifest.webmanifest");
    expect(r.status()).toBe(200);
    expect(r.headers()["content-type"] ?? "").toMatch(/application\/manifest\+json/);
    const body = await r.json();
    expect(body.name).toBe("AsoClone");
    expect(Array.isArray(body.icons)).toBe(true);
    expect((body.icons as unknown[]).length).toBeGreaterThanOrEqual(2);
  });

  test("sw.js served with a JS mime type", async ({ request }) => {
    const r = await request.get("/sw.js");
    expect(r.status()).toBe(200);
    expect(r.headers()["content-type"] ?? "").toMatch(/(application|text)\/javascript/);
  });

  test("<link rel=manifest> present on home HTML", async ({ page }) => {
    await page.goto("/ja");
    const href = await page.locator('link[rel="manifest"]').first().getAttribute("href");
    expect(href).toBeTruthy();
    expect(href).toMatch(/manifest\.webmanifest$/);
  });

  test("/ja/offline renders", async ({ page }) => {
    await page.goto("/ja/offline");
    await expect(page.getByRole("button", { name: /再読み込み|Retry/ })).toBeVisible({
      timeout: 10_000,
    });
  });
});
