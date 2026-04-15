-- Seed deterministic popularityScore values for the 50 seeded products so the
-- ai-search.spec.ts Playwright test has predictable ordering to assert on.
-- PopularityScoreSyncJob (services/search-service/.../PopularityScoreSyncJob.java)
-- reads this table on its schedule and writes scores into OpenSearch's
-- popularityScore field, which SearchQueryService boosts via function_score.
--
-- Scoring convention:
--   - Base score of 1 for every active product (so "popular" just means "visible").
--   - Aquarium products pinned to a low score on purpose to prove name-match
--     relevance beats popularity (baseline case in ai-search.spec.ts).
--   - Hot-spring products get tiered scores (1/50/100) so the ambiguous-text
--     "hot spring" query has deterministic top result.
--
-- Run once before the E2E test; rerunning is idempotent via MERGE.

MERGE INTO `asoview-clone-dev.analytics_mart.product_ranking` AS target
USING (
  SELECT
    p.product_id,
    CASE
      WHEN LOWER(p.title) LIKE '%aquarium%' THEN 1.0
      WHEN LOWER(p.title) LIKE '%hot spring top%' THEN 100.0
      WHEN LOWER(p.title) LIKE '%hot spring mid%' THEN 50.0
      WHEN LOWER(p.title) LIKE '%hot spring%' THEN 10.0
      ELSE 5.0
    END AS score,
    CURRENT_TIMESTAMP() AS updated_at
  FROM `asoview-clone-dev.analytics_mart.products` p
  WHERE p.status = 'ACTIVE'
) AS src
ON target.product_id = src.product_id
WHEN MATCHED THEN UPDATE SET
  score = src.score,
  updated_at = src.updated_at
WHEN NOT MATCHED THEN INSERT (product_id, score, updated_at)
VALUES (src.product_id, src.score, src.updated_at);
