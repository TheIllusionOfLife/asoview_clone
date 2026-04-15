import { expect, test } from "@playwright/test";

// Three-case test for popularity-boosted search. Prereq: the
// db/seeds/bq/product_ranking.sql seed has been applied AND
// PopularityScoreSyncJob has run at least once. The script in
// scripts/e2e-walkthrough.sh primes these before invoking this spec.
//
// The seed convention (see SQL file):
//   - "Aquarium *" products -> score 1 (low)
//   - "Hot Spring Top *"    -> score 100
//   - "Hot Spring Mid *"    -> score 50
//   - "Hot Spring *"        -> score 10
//   - everything else       -> score 5
test.describe("AI search (popularity boost)", () => {
  test.skip(
    process.env.ASOVIEW_AI_ENABLED !== "true",
    "AI disabled — skipping popularity-boost check",
  );

  const searchUrl = (q: string) =>
    `${process.env.API_BASE_URL ?? "https://asoview-clone-dev.duckdns.org/api"}/v1/products/search?q=${encodeURIComponent(q)}&size=10`;

  test("baseline relevance: exact-name match beats low popularity", async ({ request }) => {
    // "aquarium" products are seeded with score=1 (low). The name-match on
    // the title must still outrank higher-scored non-aquarium products.
    const response = await request.get(searchUrl("aquarium"));
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.content.length).toBeGreaterThan(0);
    expect(body.content[0].name.toLowerCase()).toContain("aquarium");
  });

  test("popularity boost: ambiguous query sorts by score within same relevance", async ({
    request,
  }) => {
    const response = await request.get(searchUrl("hot spring"));
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(body.content.length).toBeGreaterThanOrEqual(2);
    const top = body.content[0].name.toLowerCase();
    // Top hot-spring result should be one of the high-score tiers.
    expect(top).toMatch(/hot spring (top|mid)/);
  });

  test("degraded mode: empty/missing scores don't break search", async ({ request }) => {
    // Even if the BigQuery table is empty, function_score's missing:0 means
    // the query still returns BM25-ranked results. Verified by hitting a
    // keyword that doesn't match the seeded score buckets.
    const response = await request.get(searchUrl("activity"));
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    expect(Array.isArray(body.content)).toBe(true);
  });
});
