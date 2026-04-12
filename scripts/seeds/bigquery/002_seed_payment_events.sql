-- Seed payment_events in analytics_raw matching the 60 paid orders from 001_seed_order_events.sql.
-- Idempotent: uses MERGE to avoid duplicates on re-run.
-- Run: bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/002_seed_payment_events.sql

MERGE `asoview-clone-dev.analytics_raw.payment_events` AS target
USING (
  SELECT * FROM UNNEST([
    STRUCT('pay-seed-001' AS event_id, 'payment.created' AS event_type, 'pay-id-001' AS payment_id, 'ord-seed-001' AS order_id, 'PROCESSING' AS status, 'stripe' AS provider, 5000 AS amount_jpy, 'JPY' AS currency, TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 89 DAY) AS occurred_at, 'commerce-core' AS producer),
    STRUCT('pay-seed-002', 'payment.created', 'pay-id-002', 'ord-seed-002', 'PROCESSING', 'paypay', 3500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 88 DAY), 'commerce-core'),
    STRUCT('pay-seed-003', 'payment.created', 'pay-id-003', 'ord-seed-003', 'PROCESSING', 'stripe', 7200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 87 DAY), 'commerce-core'),
    STRUCT('pay-seed-004', 'payment.created', 'pay-id-004', 'ord-seed-004', 'PROCESSING', 'paypay', 4500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 85 DAY), 'commerce-core'),
    STRUCT('pay-seed-005', 'payment.created', 'pay-id-005', 'ord-seed-005', 'PROCESSING', 'stripe', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 83 DAY), 'commerce-core'),
    STRUCT('pay-seed-006', 'payment.created', 'pay-id-006', 'ord-seed-006', 'PROCESSING', 'paypay', 3100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 80 DAY), 'commerce-core'),
    STRUCT('pay-seed-007', 'payment.created', 'pay-id-007', 'ord-seed-007', 'PROCESSING', 'stripe', 6400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 78 DAY), 'commerce-core'),
    STRUCT('pay-seed-008', 'payment.created', 'pay-id-008', 'ord-seed-008', 'PROCESSING', 'stripe', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 75 DAY), 'commerce-core'),
    STRUCT('pay-seed-009', 'payment.created', 'pay-id-009', 'ord-seed-009', 'PROCESSING', 'paypay', 2500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 73 DAY), 'commerce-core'),
    STRUCT('pay-seed-010', 'payment.created', 'pay-id-010', 'ord-seed-010', 'PROCESSING', 'stripe', 9500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 70 DAY), 'commerce-core'),
    STRUCT('pay-seed-011', 'payment.created', 'pay-id-011', 'ord-seed-011', 'PROCESSING', 'stripe', 6800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 68 DAY), 'commerce-core'),
    STRUCT('pay-seed-012', 'payment.created', 'pay-id-012', 'ord-seed-012', 'PROCESSING', 'paypay', 4200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 65 DAY), 'commerce-core'),
    STRUCT('pay-seed-013', 'payment.created', 'pay-id-013', 'ord-seed-013', 'PROCESSING', 'stripe', 5500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 63 DAY), 'commerce-core'),
    STRUCT('pay-seed-014', 'payment.created', 'pay-id-014', 'ord-seed-014', 'PROCESSING', 'paypay', 3800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 60 DAY), 'commerce-core'),
    STRUCT('pay-seed-015', 'payment.created', 'pay-id-015', 'ord-seed-015', 'PROCESSING', 'stripe', 11000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 58 DAY), 'commerce-core'),
    STRUCT('pay-seed-016', 'payment.created', 'pay-id-016', 'ord-seed-016', 'PROCESSING', 'paypay', 7500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 55 DAY), 'commerce-core'),
    STRUCT('pay-seed-017', 'payment.created', 'pay-id-017', 'ord-seed-017', 'PROCESSING', 'stripe', 4100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 53 DAY), 'commerce-core'),
    STRUCT('pay-seed-018', 'payment.created', 'pay-id-018', 'ord-seed-018', 'PROCESSING', 'stripe', 8800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 50 DAY), 'commerce-core'),
    STRUCT('pay-seed-019', 'payment.created', 'pay-id-019', 'ord-seed-019', 'PROCESSING', 'paypay', 3200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 48 DAY), 'commerce-core'),
    STRUCT('pay-seed-020', 'payment.created', 'pay-id-020', 'ord-seed-020', 'PROCESSING', 'stripe', 6100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 45 DAY), 'commerce-core'),
    STRUCT('pay-seed-021', 'payment.created', 'pay-id-021', 'ord-seed-021', 'PROCESSING', 'paypay', 9200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 43 DAY), 'commerce-core'),
    STRUCT('pay-seed-022', 'payment.created', 'pay-id-022', 'ord-seed-022', 'PROCESSING', 'stripe', 5800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 40 DAY), 'commerce-core'),
    STRUCT('pay-seed-023', 'payment.created', 'pay-id-023', 'ord-seed-023', 'PROCESSING', 'paypay', 4700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 38 DAY), 'commerce-core'),
    STRUCT('pay-seed-024', 'payment.created', 'pay-id-024', 'ord-seed-024', 'PROCESSING', 'stripe', 13500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 35 DAY), 'commerce-core'),
    STRUCT('pay-seed-025', 'payment.created', 'pay-id-025', 'ord-seed-025', 'PROCESSING', 'paypay', 2800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 33 DAY), 'commerce-core'),
    STRUCT('pay-seed-026', 'payment.created', 'pay-id-026', 'ord-seed-026', 'PROCESSING', 'stripe', 7800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 30 DAY), 'commerce-core'),
    STRUCT('pay-seed-027', 'payment.created', 'pay-id-027', 'ord-seed-027', 'PROCESSING', 'paypay', 5100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 28 DAY), 'commerce-core'),
    STRUCT('pay-seed-028', 'payment.created', 'pay-id-028', 'ord-seed-028', 'PROCESSING', 'stripe', 9900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 25 DAY), 'commerce-core'),
    STRUCT('pay-seed-029', 'payment.created', 'pay-id-029', 'ord-seed-029', 'PROCESSING', 'paypay', 4400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 23 DAY), 'commerce-core'),
    STRUCT('pay-seed-030', 'payment.created', 'pay-id-030', 'ord-seed-030', 'PROCESSING', 'stripe', 6700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 20 DAY), 'commerce-core'),
    STRUCT('pay-seed-031', 'payment.created', 'pay-id-031', 'ord-seed-031', 'PROCESSING', 'paypay', 8100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 18 DAY), 'commerce-core'),
    STRUCT('pay-seed-032', 'payment.created', 'pay-id-032', 'ord-seed-032', 'PROCESSING', 'stripe', 3900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 16 DAY), 'commerce-core'),
    STRUCT('pay-seed-033', 'payment.created', 'pay-id-033', 'ord-seed-033', 'PROCESSING', 'paypay', 7400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 14 DAY), 'commerce-core'),
    STRUCT('pay-seed-034', 'payment.created', 'pay-id-034', 'ord-seed-034', 'PROCESSING', 'stripe', 5200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 12 DAY), 'commerce-core'),
    STRUCT('pay-seed-035', 'payment.created', 'pay-id-035', 'ord-seed-035', 'PROCESSING', 'paypay', 10500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 10 DAY), 'commerce-core'),
    STRUCT('pay-seed-036', 'payment.created', 'pay-id-036', 'ord-seed-036', 'PROCESSING', 'stripe', 4600, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 9 DAY), 'commerce-core'),
    STRUCT('pay-seed-037', 'payment.created', 'pay-id-037', 'ord-seed-037', 'PROCESSING', 'paypay', 6200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 8 DAY), 'commerce-core'),
    STRUCT('pay-seed-038', 'payment.created', 'pay-id-038', 'ord-seed-038', 'PROCESSING', 'stripe', 8500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY), 'commerce-core'),
    STRUCT('pay-seed-039', 'payment.created', 'pay-id-039', 'ord-seed-039', 'PROCESSING', 'paypay', 3700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 6 DAY), 'commerce-core'),
    STRUCT('pay-seed-040', 'payment.created', 'pay-id-040', 'ord-seed-040', 'PROCESSING', 'stripe', 14200, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 5 DAY), 'commerce-core'),
    STRUCT('pay-seed-041', 'payment.created', 'pay-id-041', 'ord-seed-041', 'PROCESSING', 'paypay', 5600, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 4 DAY), 'commerce-core'),
    STRUCT('pay-seed-042', 'payment.created', 'pay-id-042', 'ord-seed-042', 'PROCESSING', 'stripe', 7100, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 DAY), 'commerce-core'),
    STRUCT('pay-seed-043', 'payment.created', 'pay-id-043', 'ord-seed-043', 'PROCESSING', 'paypay', 4300, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 3 DAY), 'commerce-core'),
    STRUCT('pay-seed-044', 'payment.created', 'pay-id-044', 'ord-seed-044', 'PROCESSING', 'stripe', 9800, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core'),
    STRUCT('pay-seed-045', 'payment.created', 'pay-id-045', 'ord-seed-045', 'PROCESSING', 'paypay', 6500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core'),
    STRUCT('pay-seed-046', 'payment.created', 'pay-id-046', 'ord-seed-046', 'PROCESSING', 'stripe', 3400, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core'),
    STRUCT('pay-seed-047', 'payment.created', 'pay-id-047', 'ord-seed-047', 'PROCESSING', 'paypay', 11500, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core'),
    STRUCT('pay-seed-048', 'payment.created', 'pay-id-048', 'ord-seed-048', 'PROCESSING', 'stripe', 5300, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY), 'commerce-core'),
    STRUCT('pay-seed-049', 'payment.created', 'pay-id-049', 'ord-seed-049', 'PROCESSING', 'paypay', 7700, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 0 DAY), 'commerce-core'),
    STRUCT('pay-seed-050', 'payment.created', 'pay-id-050', 'ord-seed-050', 'PROCESSING', 'stripe', 4900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 0 DAY), 'commerce-core'),
    STRUCT('pay-seed-051', 'payment.created', 'pay-id-051', 'ord-seed-051', 'PROCESSING', 'paypay', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 42 DAY), 'commerce-core'),
    STRUCT('pay-seed-052', 'payment.created', 'pay-id-052', 'ord-seed-052', 'PROCESSING', 'stripe', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 37 DAY), 'commerce-core'),
    STRUCT('pay-seed-053', 'payment.created', 'pay-id-053', 'ord-seed-053', 'PROCESSING', 'paypay', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 32 DAY), 'commerce-core'),
    STRUCT('pay-seed-054', 'payment.created', 'pay-id-054', 'ord-seed-054', 'PROCESSING', 'stripe', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 27 DAY), 'commerce-core'),
    STRUCT('pay-seed-055', 'payment.created', 'pay-id-055', 'ord-seed-055', 'PROCESSING', 'paypay', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 22 DAY), 'commerce-core'),
    STRUCT('pay-seed-056', 'payment.created', 'pay-id-056', 'ord-seed-056', 'PROCESSING', 'stripe', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 17 DAY), 'commerce-core'),
    STRUCT('pay-seed-057', 'payment.created', 'pay-id-057', 'ord-seed-057', 'PROCESSING', 'paypay', 5000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 11 DAY), 'commerce-core'),
    STRUCT('pay-seed-058', 'payment.created', 'pay-id-058', 'ord-seed-058', 'PROCESSING', 'stripe', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 8 DAY), 'commerce-core'),
    STRUCT('pay-seed-059', 'payment.created', 'pay-id-059', 'ord-seed-059', 'PROCESSING', 'paypay', 8900, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 4 DAY), 'commerce-core'),
    STRUCT('pay-seed-060', 'payment.created', 'pay-id-060', 'ord-seed-060', 'PROCESSING', 'stripe', 12000, 'JPY', TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 2 DAY), 'commerce-core')
  ])
) AS source
ON target.event_id = source.event_id
WHEN NOT MATCHED THEN
  INSERT (event_id, event_type, payment_id, order_id, status, provider, amount_jpy, currency, occurred_at, producer)
  VALUES (source.event_id, source.event_type, source.payment_id, source.order_id, source.status, source.provider, source.amount_jpy, source.currency, source.occurred_at, source.producer);
