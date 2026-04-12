-- Seed order_events in analytics_raw with realistic data matching the 50 Postgres seed products.
-- Idempotent: uses MERGE to avoid duplicates on re-run.
-- Run: bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/001_seed_order_events.sql

MERGE `asoview-clone-dev.analytics_raw.order_events` AS target
USING (
  SELECT * FROM UNNEST([
    -- 60 order.paid events spread across 90 days, 10 users, 50 products
    STRUCT('evt-seed-001' AS event_id, 'order.paid' AS event_type, 'ord-seed-001' AS order_id, 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5' AS user_id, 'PAID' AS status, 5000 AS subtotal_jpy, 'JPY' AS currency, TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 89 DAY) AS occurred_at, 'commerce-core' AS producer, 'c4e00660-a232-5634-9daa-59362df77a59' AS product_id),
    STRUCT('evt-seed-002', 'order.paid', 'ord-seed-002', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 3500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 88 DAY), 'commerce-core', '78a12dc6-4b97-5ed1-a46b-7bb869eb50b9'),
    STRUCT('evt-seed-003', 'order.paid', 'ord-seed-003', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 7200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 87 DAY), 'commerce-core', '80f2518d-343d-53b7-aadd-012f90b881b0'),
    STRUCT('evt-seed-004', 'order.paid', 'ord-seed-004', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 4500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 85 DAY), 'commerce-core', '5b2e1005-4d79-5f8b-8a3b-baf408a9b6d5'),
    STRUCT('evt-seed-005', 'order.paid', 'ord-seed-005', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 83 DAY), 'commerce-core', 'c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0'),
    STRUCT('evt-seed-006', 'order.paid', 'ord-seed-006', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 3100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 80 DAY), 'commerce-core', 'bb8a11ef-8bee-58f7-b948-dbe2f50bfb42'),
    STRUCT('evt-seed-007', 'order.paid', 'ord-seed-007', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 6400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 78 DAY), 'commerce-core', '4b5e939f-b5c8-5e69-b1ab-2df406aaeb77'),
    STRUCT('evt-seed-008', 'order.paid', 'ord-seed-008', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 75 DAY), 'commerce-core', '91c66d69-ff03-5c25-b9b4-c039a74babe9'),
    STRUCT('evt-seed-009', 'order.paid', 'ord-seed-009', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 2500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 73 DAY), 'commerce-core', '477b4d8d-e852-5c44-9038-c873d03a01fd'),
    STRUCT('evt-seed-010', 'order.paid', 'ord-seed-010', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 9500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 70 DAY), 'commerce-core', '26e0743c-1b19-571a-aa3e-1a9abec5de99'),
    -- Users re-ordering (repeat purchases for funnel analysis)
    STRUCT('evt-seed-011', 'order.paid', 'ord-seed-011', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'PAID', 6800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 68 DAY), 'commerce-core', '2ceaa43a-2cf8-5a86-906c-325baaaeb8d6'),
    STRUCT('evt-seed-012', 'order.paid', 'ord-seed-012', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 4200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 65 DAY), 'commerce-core', '51c782b5-1ed9-5615-aa26-328309ff704e'),
    STRUCT('evt-seed-013', 'order.paid', 'ord-seed-013', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 5500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 63 DAY), 'commerce-core', '18178174-e946-5c0b-858b-1b8ab12f62a0'),
    STRUCT('evt-seed-014', 'order.paid', 'ord-seed-014', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 3800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 60 DAY), 'commerce-core', '2e35de31-3068-5fd5-8be4-a739a3256b84'),
    STRUCT('evt-seed-015', 'order.paid', 'ord-seed-015', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 11000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 58 DAY), 'commerce-core', 'f0a744d3-f311-5f04-b903-10c521fcb569'),
    STRUCT('evt-seed-016', 'order.paid', 'ord-seed-016', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 7500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 55 DAY), 'commerce-core', '82f785aa-b492-57e9-8acf-ac2f40d706f0'),
    STRUCT('evt-seed-017', 'order.paid', 'ord-seed-017', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 4100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 53 DAY), 'commerce-core', '3f1a6f9f-da35-5b25-9411-7f3c7c8d88b3'),
    STRUCT('evt-seed-018', 'order.paid', 'ord-seed-018', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 8800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 50 DAY), 'commerce-core', 'bbe013e5-f3dd-529a-9cf5-7336d498e04f'),
    STRUCT('evt-seed-019', 'order.paid', 'ord-seed-019', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 3200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 48 DAY), 'commerce-core', '336bbcdc-c7fb-537b-86e2-8f74494554f3'),
    STRUCT('evt-seed-020', 'order.paid', 'ord-seed-020', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 6100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 45 DAY), 'commerce-core', '1186c1f0-14bf-55d2-8b03-8ebc6726e830'),
    -- More orders across different products
    STRUCT('evt-seed-021', 'order.paid', 'ord-seed-021', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'PAID', 9200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 43 DAY), 'commerce-core', 'f12d3420-34c8-58c3-84ef-204b3c25183b'),
    STRUCT('evt-seed-022', 'order.paid', 'ord-seed-022', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 5800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 40 DAY), 'commerce-core', 'c6b9233d-1e58-5387-8fd8-18a085f7a9ad'),
    STRUCT('evt-seed-023', 'order.paid', 'ord-seed-023', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 4700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 38 DAY), 'commerce-core', 'a208a91a-24ce-51d7-b4f0-4842906c11b6'),
    STRUCT('evt-seed-024', 'order.paid', 'ord-seed-024', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 13500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 35 DAY), 'commerce-core', '577b144e-4b16-5413-bc42-e37c38f417dd'),
    STRUCT('evt-seed-025', 'order.paid', 'ord-seed-025', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 2800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 33 DAY), 'commerce-core', 'b7072871-76fd-5948-bafa-3af5c632c768'),
    STRUCT('evt-seed-026', 'order.paid', 'ord-seed-026', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 7800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 30 DAY), 'commerce-core', '5e217159-1ce0-5bf4-8750-f8e405c4cc3b'),
    STRUCT('evt-seed-027', 'order.paid', 'ord-seed-027', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 5100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 28 DAY), 'commerce-core', 'cb18f02f-c239-5029-9430-29181489d4cf'),
    STRUCT('evt-seed-028', 'order.paid', 'ord-seed-028', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 9900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 25 DAY), 'commerce-core', '97aa22c7-2cdc-54fa-8901-a95aac79931e'),
    STRUCT('evt-seed-029', 'order.paid', 'ord-seed-029', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 4400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 23 DAY), 'commerce-core', 'ac9a2f63-d227-57f7-aaf4-490a25a6e690'),
    STRUCT('evt-seed-030', 'order.paid', 'ord-seed-030', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 6700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 20 DAY), 'commerce-core', 'a4ddce7a-0226-570a-8249-7f6f273bcb3b'),
    -- Recent orders (last 20 days)
    STRUCT('evt-seed-031', 'order.paid', 'ord-seed-031', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'PAID', 8100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 18 DAY), 'commerce-core', '3f727b6f-e1f0-5b55-9170-de6fba485c83'),
    STRUCT('evt-seed-032', 'order.paid', 'ord-seed-032', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 3900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 16 DAY), 'commerce-core', '2a64267d-043f-50e0-a860-7debb43ce116'),
    STRUCT('evt-seed-033', 'order.paid', 'ord-seed-033', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 7400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY), 'commerce-core', 'cd8eb4bc-9e91-5a93-96d0-c389102802a6'),
    STRUCT('evt-seed-034', 'order.paid', 'ord-seed-034', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 5200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 12 DAY), 'commerce-core', 'c7a89317-473c-5d6a-82a7-c711936fe180'),
    STRUCT('evt-seed-035', 'order.paid', 'ord-seed-035', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 10500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 10 DAY), 'commerce-core', '7c427a3e-7cef-5a77-8585-ad6b83b6c18b'),
    STRUCT('evt-seed-036', 'order.paid', 'ord-seed-036', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 4600, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 9 DAY), 'commerce-core', '20cbea18-3bd5-54ec-8b1c-c8fe9eb7a144'),
    STRUCT('evt-seed-037', 'order.paid', 'ord-seed-037', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 6200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 8 DAY), 'commerce-core', '80bf2ca6-8eca-5476-be89-937024409092'),
    STRUCT('evt-seed-038', 'order.paid', 'ord-seed-038', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 8500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY), 'commerce-core', 'f9061149-6379-5ba9-aef6-658ebebc1719'),
    STRUCT('evt-seed-039', 'order.paid', 'ord-seed-039', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 3700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 6 DAY), 'commerce-core', '77ebc439-2770-53cf-821a-482fbdaf343d'),
    STRUCT('evt-seed-040', 'order.paid', 'ord-seed-040', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 14200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 5 DAY), 'commerce-core', '3befe340-57b7-5576-8f8b-489e5fc5f546'),
    -- Additional variety: products 41-50 (most recent)
    STRUCT('evt-seed-041', 'order.paid', 'ord-seed-041', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'PAID', 5600, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 4 DAY), 'commerce-core', 'a263d367-c54c-5e25-bbf8-ef68ff81930b'),
    STRUCT('evt-seed-042', 'order.paid', 'ord-seed-042', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 7100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 DAY), 'commerce-core', '52462038-e970-5ec3-82af-b0c0ad5593bd'),
    STRUCT('evt-seed-043', 'order.paid', 'ord-seed-043', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 4300, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 DAY), 'commerce-core', '2e82262d-4773-5f0e-a4cf-c17af1b4c0b3'),
    STRUCT('evt-seed-044', 'order.paid', 'ord-seed-044', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 9800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core', 'de0a6197-dd4b-51b1-b7ff-f63bb98758c6'),
    STRUCT('evt-seed-045', 'order.paid', 'ord-seed-045', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 6500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core', 'd84c7101-3a7a-52fc-8616-f6cc0bcca95f'),
    STRUCT('evt-seed-046', 'order.paid', 'ord-seed-046', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 3400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core', '0b6eb43a-e6bb-55b3-b290-98954da3d457'),
    STRUCT('evt-seed-047', 'order.paid', 'ord-seed-047', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 11500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core', '0a2c7440-97cb-5118-85bc-c310b0720707'),
    STRUCT('evt-seed-048', 'order.paid', 'ord-seed-048', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 5300, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core', '3c182c29-4c3b-5a3c-89b0-8a2689412747'),
    STRUCT('evt-seed-049', 'order.paid', 'ord-seed-049', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 7700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 0 DAY), 'commerce-core', 'ffba0eac-2a9a-5347-8188-3cc9157c10b4'),
    STRUCT('evt-seed-050', 'order.paid', 'ord-seed-050', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 4900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 0 DAY), 'commerce-core', 'eec7fb2a-d455-51e7-be38-1295e1fbf300'),
    -- Repeat purchases for popular products (boosting product_ranking)
    STRUCT('evt-seed-051', 'order.paid', 'ord-seed-051', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'PAID', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 42 DAY), 'commerce-core', 'c4e00660-a232-5634-9daa-59362df77a59'),
    STRUCT('evt-seed-052', 'order.paid', 'ord-seed-052', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'PAID', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 37 DAY), 'commerce-core', '91c66d69-ff03-5c25-b9b4-c039a74babe9'),
    STRUCT('evt-seed-053', 'order.paid', 'ord-seed-053', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'PAID', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 32 DAY), 'commerce-core', 'c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0'),
    STRUCT('evt-seed-054', 'order.paid', 'ord-seed-054', '726adebc-fc23-5220-9636-d10ffacafc13', 'PAID', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 27 DAY), 'commerce-core', '91c66d69-ff03-5c25-b9b4-c039a74babe9'),
    STRUCT('evt-seed-055', 'order.paid', 'ord-seed-055', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'PAID', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 22 DAY), 'commerce-core', 'c4e00660-a232-5634-9daa-59362df77a59'),
    STRUCT('evt-seed-056', 'order.paid', 'ord-seed-056', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'PAID', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 17 DAY), 'commerce-core', 'c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0'),
    STRUCT('evt-seed-057', 'order.paid', 'ord-seed-057', '604e0989-39bd-5288-a749-6e3eee7855d2', 'PAID', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 11 DAY), 'commerce-core', 'c4e00660-a232-5634-9daa-59362df77a59'),
    STRUCT('evt-seed-058', 'order.paid', 'ord-seed-058', '217bb94b-37e3-5849-8e68-0e4ef4cbda1b', 'PAID', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 8 DAY), 'commerce-core', '91c66d69-ff03-5c25-b9b4-c039a74babe9'),
    STRUCT('evt-seed-059', 'order.paid', 'ord-seed-059', '176aa095-c00f-5ece-8c95-75d2db12a875', 'PAID', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 4 DAY), 'commerce-core', 'c8dc7ef9-9bba-51f5-a48a-e95f0c6749c0'),
    STRUCT('evt-seed-060', 'order.paid', 'ord-seed-060', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'PAID', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core', '91c66d69-ff03-5c25-b9b4-c039a74babe9'),
    -- 8 cancelled orders
    STRUCT('evt-seed-061', 'order.cancelled', 'ord-seed-061', 'ffedb4a8-3b87-5dc9-b868-2656dfaac1a5', 'CANCELLED', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 82 DAY), 'commerce-core', '4b5e939f-b5c8-5e69-b1ab-2df406aaeb77'),
    STRUCT('evt-seed-062', 'order.cancelled', 'ord-seed-062', '9e4e9269-d647-5d80-9a70-ef0c2641a9bd', 'CANCELLED', 3500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 72 DAY), 'commerce-core', '82f785aa-b492-57e9-8acf-ac2f40d706f0'),
    STRUCT('evt-seed-063', 'order.cancelled', 'ord-seed-063', 'c1b36bd8-5087-574c-82af-56077b6562a0', 'CANCELLED', 7200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 62 DAY), 'commerce-core', 'f0a744d3-f311-5f04-b903-10c521fcb569'),
    STRUCT('evt-seed-064', 'order.cancelled', 'ord-seed-064', '88cd3740-abdf-57a9-90bd-4b5b5674ca32', 'CANCELLED', 4500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 52 DAY), 'commerce-core', '3f1a6f9f-da35-5b25-9411-7f3c7c8d88b3'),
    STRUCT('evt-seed-065', 'order.cancelled', 'ord-seed-065', '726adebc-fc23-5220-9636-d10ffacafc13', 'CANCELLED', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 42 DAY), 'commerce-core', 'bbe013e5-f3dd-529a-9cf5-7336d498e04f'),
    STRUCT('evt-seed-066', 'order.cancelled', 'ord-seed-066', '21073ca8-a48a-552c-9b21-f96b4b6084c3', 'CANCELLED', 3100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 32 DAY), 'commerce-core', '336bbcdc-c7fb-537b-86e2-8f74494554f3'),
    STRUCT('evt-seed-067', 'order.cancelled', 'ord-seed-067', '8e03e7ed-6320-5d36-b705-546dcbf8328b', 'CANCELLED', 6400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 15 DAY), 'commerce-core', '1186c1f0-14bf-55d2-8b03-8ebc6726e830'),
    STRUCT('evt-seed-068', 'order.cancelled', 'ord-seed-068', '604e0989-39bd-5288-a749-6e3eee7855d2', 'CANCELLED', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 5 DAY), 'commerce-core', 'f12d3420-34c8-58c3-84ef-204b3c25183b')
  ])
) AS source
ON target.event_id = source.event_id
WHEN NOT MATCHED THEN
  INSERT (event_id, event_type, order_id, user_id, status, subtotal_jpy, currency, occurred_at, producer, product_id)
  VALUES (source.event_id, source.event_type, source.order_id, source.user_id, source.status, source.subtotal_jpy, source.currency, source.occurred_at, source.producer, source.product_id);
