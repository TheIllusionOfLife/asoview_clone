import { type APIRequestContext, expect, test } from "@playwright/test";

/**
 * Live-cluster search scenarios. Covers the observable behaviour that
 * `VertexAiSearchQueryServiceTest` cannot check (real data store, real
 * tokenizer, real sort on `structData.minPrice`, real CJK match against
 * the seeded Japanese translations).
 *
 * Invoked manually with
 *   PLAYWRIGHT_BASE_URL=https://asoview-clone-dev.duckdns.org \
 *   API_BASE_URL=https://asoview-clone-dev.duckdns.org/api \
 *   bun run test:e2e -- e2e/smoke/search-scenarios.spec.ts
 * Not included in the default playwright.config.ts `testMatch` list so
 * the localhost CI lane doesn't try to hit the live cluster.
 */

const apiBase = process.env.API_BASE_URL ?? "https://asoview-clone-dev.duckdns.org/api";

type SearchHit = {
  productId: string;
  name?: string | null;
  minPrice?: number | null;
  areaId?: string | null;
  categoryId?: string | null;
};

type SearchResponse = {
  content: SearchHit[];
  totalElements: number;
  number: number;
  size: number;
};

const searchUrl = (params: Record<string, string | number>) => {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) qs.set(k, String(v));
  return `${apiBase}/v1/search?${qs.toString()}`;
};

async function fetchSearch(
  request: APIRequestContext,
  params: Record<string, string | number>,
): Promise<SearchResponse> {
  const response = await request.get(searchUrl(params));
  expect(
    response.ok(),
    `search ${JSON.stringify(params)} → HTTP ${response.status()}`,
  ).toBeTruthy();
  return (await response.json()) as SearchResponse;
}

/**
 * Waits up to ~20s for a search query to return at least `minHits` hits.
 * The commerce-core → search-service indexer runs asynchronously after
 * the DB commit (DevSearchReindexer @TransactionalEventListener), so a
 * freshly re-seeded dev cluster can answer 0 results for a few seconds
 * before the indexer catches up.
 */
async function pollUntilIndexed(
  request: APIRequestContext,
  params: Record<string, string | number>,
  minHits: number,
): Promise<SearchResponse> {
  const deadline = Date.now() + 20_000;
  let last: SearchResponse = { content: [], totalElements: 0, number: 0, size: 0 };
  while (Date.now() < deadline) {
    last = await fetchSearch(request, params);
    if (last.totalElements >= minHits) return last;
    await new Promise((r) => setTimeout(r, 1_500));
  }
  throw new Error(
    `search ${JSON.stringify(params)} never returned ≥${minHits} hits; last: total=${last.totalElements}`,
  );
}

test.describe("live search scenarios", () => {
  test("baseline: match-all returns 50 products", async ({ request }) => {
    const body = await pollUntilIndexed(request, { size: 50 }, 50);
    expect(body.totalElements).toBeGreaterThanOrEqual(50);
    expect(body.content.length).toBeGreaterThanOrEqual(50);
  });

  test("category filter narrows result set to that category", async ({ request }) => {
    // Pick any category from the all-results page and assert every hit
    // on a second request filtered by that category carries the same id.
    const all = await fetchSearch(request, { size: 50 });
    const first = all.content.find((h) => h.categoryId);
    if (!first?.categoryId) test.skip(true, "no categoryId on any hit; index not ready");
    const targetCategory = first?.categoryId as string;

    const filtered = await fetchSearch(request, { category: targetCategory, size: 50 });
    expect(filtered.content.length).toBeGreaterThan(0);
    for (const h of filtered.content) {
      expect(h.categoryId).toBe(targetCategory);
    }
  });

  test("sort=price_asc returns monotonically non-decreasing minPrice", async ({ request }) => {
    const body = await fetchSearch(request, { sort: "price_asc", size: 20 });
    const prices = body.content
      .map((h) => h.minPrice)
      .filter((p): p is number => typeof p === "number");
    expect(prices.length).toBeGreaterThan(1);
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i], `prices[${i}] < prices[${i - 1}]`).toBeGreaterThanOrEqual(
        prices[i - 1] as number,
      );
    }
  });

  test("sort=price_desc returns monotonically non-increasing minPrice", async ({ request }) => {
    const body = await fetchSearch(request, { sort: "price_desc", size: 20 });
    const prices = body.content
      .map((h) => h.minPrice)
      .filter((p): p is number => typeof p === "number");
    expect(prices.length).toBeGreaterThan(1);
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i], `prices[${i}] > prices[${i - 1}]`).toBeLessThanOrEqual(
        prices[i - 1] as number,
      );
    }
  });

  test("price range filter constrains every hit into [minPrice, maxPrice]", async ({ request }) => {
    const lower = 2000;
    const upper = 4000;
    const body = await fetchSearch(request, { minPrice: lower, maxPrice: upper, size: 50 });
    for (const h of body.content) {
      expect(
        h.minPrice,
        `minPrice ${h.minPrice} out of [${lower}, ${upper}]`,
      ).toBeGreaterThanOrEqual(lower);
      expect(h.minPrice).toBeLessThanOrEqual(upper);
    }
  });

  test("pagination returns disjoint product ids across pages", async ({ request }) => {
    const pageSize = 10;
    const p0 = await fetchSearch(request, { page: 0, size: pageSize });
    const p1 = await fetchSearch(request, { page: 1, size: pageSize });
    const ids0 = new Set(p0.content.map((h) => h.productId));
    const overlap = p1.content.filter((h) => ids0.has(h.productId));
    expect(overlap).toHaveLength(0);
    expect(p0.content.length).toBe(pageSize);
    expect(p1.content.length).toBeGreaterThan(0);
  });

  test("CJK query 温泉 resolves to Japanese hot-spring seed", async ({ request }) => {
    const body = await pollUntilIndexed(request, { q: "温泉", size: 10 }, 1);
    expect(body.totalElements).toBeGreaterThan(0);
  });

  test("empty-query + unknown-term behaviour", async ({ request }) => {
    const unknown = await fetchSearch(request, { q: "xyzzy-not-in-any-title", size: 5 });
    expect(Array.isArray(unknown.content)).toBe(true);
    expect(unknown.totalElements).toBe(0);
  });
});
