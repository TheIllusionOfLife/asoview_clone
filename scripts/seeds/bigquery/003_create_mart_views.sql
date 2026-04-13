-- Create analytics_mart views over analytics_raw tables.
-- Idempotent: CREATE OR REPLACE VIEW.
-- Run: bq query --use_legacy_sql=false --project_id=asoview-clone-dev < scripts/seeds/bigquery/003_create_mart_views.sql

-- Daily bookings aggregation
CREATE OR REPLACE VIEW `asoview-clone-dev.analytics_mart.daily_bookings` AS
SELECT
  DATE(occurred_at, 'Asia/Tokyo') AS booking_date,
  COUNT(DISTINCT order_id) AS order_count,
  SUM(subtotal_jpy) AS revenue_jpy,
  SAFE_DIVIDE(SUM(subtotal_jpy), COUNT(DISTINCT order_id)) AS avg_order_value_jpy
FROM `asoview-clone-dev.analytics_raw.order_events`
WHERE event_type = 'order.paid'
GROUP BY 1;

-- Product ranking by order count and revenue
CREATE OR REPLACE VIEW `asoview-clone-dev.analytics_mart.product_ranking` AS
SELECT
  product_id,
  COUNT(DISTINCT order_id) AS order_count,
  SUM(subtotal_jpy) AS total_revenue_jpy,
  RANK() OVER (ORDER BY COUNT(DISTINCT order_id) DESC) AS popularity_rank
FROM `asoview-clone-dev.analytics_raw.order_events`
WHERE event_type = 'order.paid' AND product_id IS NOT NULL
GROUP BY 1;

-- Venue performance (joins product_venue_mapping reference table)
CREATE OR REPLACE VIEW `asoview-clone-dev.analytics_mart.venue_performance` AS
SELECT
  m.venue_id,
  m.venue_name,
  COUNT(DISTINCT e.order_id) AS order_count,
  SUM(e.subtotal_jpy) AS total_revenue_jpy,
  SAFE_DIVIDE(SUM(e.subtotal_jpy), COUNT(DISTINCT e.order_id)) AS avg_order_value_jpy
FROM `asoview-clone-dev.analytics_raw.order_events` e
JOIN `asoview-clone-dev.analytics_raw.product_venue_mapping` m
  ON e.product_id = m.product_id
WHERE e.event_type = 'order.paid'
GROUP BY 1, 2;

-- Consumer funnel (user lifetime metrics)
CREATE OR REPLACE VIEW `asoview-clone-dev.analytics_mart.consumer_funnel` AS
SELECT
  user_id,
  MIN(DATE(occurred_at, 'Asia/Tokyo')) AS first_order_date,
  MAX(DATE(occurred_at, 'Asia/Tokyo')) AS last_order_date,
  COUNT(DISTINCT order_id) AS total_orders,
  SUM(subtotal_jpy) AS ltv_jpy,
  SAFE_DIVIDE(SUM(subtotal_jpy), COUNT(DISTINCT order_id)) AS avg_order_value_jpy
FROM `asoview-clone-dev.analytics_raw.order_events`
WHERE event_type = 'order.paid'
GROUP BY 1;
