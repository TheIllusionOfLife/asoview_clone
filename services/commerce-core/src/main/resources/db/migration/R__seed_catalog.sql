-- Repeatable seed for local/dev catalog. No-op unless Flyway placeholder
-- `seed_catalog=true` is set. Idempotent: every INSERT uses a deterministic
-- uuid_generate_v5 id and ON CONFLICT (id) DO NOTHING so re-running is safe.
--
-- Targets (docs/technical_design.md §16):
--   8 venues (one per area: Tokyo / Yokohama / Kyoto / Osaka / Sapporo /
--             Fukuoka / Okinawa / Nagoya)
--   4 categories (Outdoor / Indoor / Food / Culture)
--   50 products (title + translations ja/en, ACTIVE, spread across venues+categories)
--   100 product_variants (2 per product, NUMERIC money as strings)
--   10 demo users (deterministic firebase uids: demo01..demo10)
--   50 reviews
--   product_review_aggregates rolled up

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
BEGIN
  IF '${seed_catalog}' <> 'true' THEN
    RAISE NOTICE 'R__seed_catalog: seed_catalog placeholder not true, skipping';
    RETURN;
  END IF;

  -- ===== Tenant (singleton) =====
  INSERT INTO tenants (id, name, slug, created_by, updated_by)
  VALUES (
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:tenant:default'),
    'Asoview Clone Demo',
    'default',
    'seed', 'seed'
  ) ON CONFLICT (id) DO NOTHING;

  -- ===== Venues (acting as "areas") =====
  INSERT INTO venues (id, tenant_id, name, address, latitude, longitude, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:venue:' || v.slug),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:tenant:default'),
    v.name,
    v.addr,
    v.lat,
    v.lng,
    'seed', 'seed'
  FROM (VALUES
    ('tokyo',    'Tokyo',    'Tokyo, Japan',    35.6762, 139.6503),
    ('yokohama', 'Yokohama', 'Yokohama, Japan', 35.4437, 139.6380),
    ('kyoto',    'Kyoto',    'Kyoto, Japan',    35.0116, 135.7681),
    ('osaka',    'Osaka',    'Osaka, Japan',    34.6937, 135.5023),
    ('sapporo',  'Sapporo',  'Sapporo, Japan',  43.0618, 141.3545),
    ('fukuoka',  'Fukuoka',  'Fukuoka, Japan',  33.5904, 130.4017),
    ('okinawa',  'Okinawa',  'Okinawa, Japan',  26.2124, 127.6809),
    ('nagoya',   'Nagoya',   'Nagoya, Japan',   35.1815, 136.9066)
  ) AS v(slug, name, addr, lat, lng)
  ON CONFLICT (id) DO NOTHING;

  -- ===== Categories =====
  INSERT INTO categories (id, name, slug, display_order, image_url, translations, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:category:' || c.slug),
    c.name,
    c.slug,
    c.ord,
    'https://images.unsplash.com/photo-' || c.img || '?w=640',
    jsonb_build_object(
      'ja', jsonb_build_object('name', c.ja),
      'en', jsonb_build_object('name', c.name)
    ),
    'seed', 'seed'
  FROM (VALUES
    ('outdoor', 'Outdoor', 'アウトドア', 1, '1551632811-561732d1e306'),
    ('indoor',  'Indoor',  'インドア',   2, '1533174072545-7a4b6ad7a6c3'),
    ('food',    'Food',    'グルメ',     3, '1504674900247-0877df9cc836'),
    ('culture', 'Culture', 'カルチャー', 4, '1528164344705-47542687000d')
  ) AS c(slug, name, ja, ord, img)
  ON CONFLICT (id) DO NOTHING;

  -- ===== Products (50) =====
  -- Deterministically spread: product N maps to venue[N % 8] and category[N % 4].
  INSERT INTO products (id, tenant_id, venue_id, category_id, title, description, image_url, status, translations, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:product:' || p.n),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:tenant:default'),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:venue:' || (ARRAY['tokyo','yokohama','kyoto','osaka','sapporo','fukuoka','okinawa','nagoya'])[1 + (p.n % 8)]),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:category:' || (ARRAY['outdoor','indoor','food','culture'])[1 + (p.n % 4)]),
    'Demo Experience #' || p.n,
    'A seeded demo activity number ' || p.n || ' for local development. Not a real product.',
    'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200&sig=' || p.n,
    'ACTIVE',
    jsonb_build_object(
      'ja', jsonb_build_object('name', 'デモ体験 #' || p.n, 'description', 'ローカル開発用のシードデータ #' || p.n),
      'en', jsonb_build_object('name', 'Demo Experience #' || p.n, 'description', 'Seeded demo activity #' || p.n)
    ),
    'seed', 'seed'
  FROM generate_series(1, 50) AS p(n)
  ON CONFLICT (id) DO NOTHING;

  -- ===== Product variants (2 per product = 100) =====
  INSERT INTO product_variants (id, product_id, name, price_amount, price_currency, duration_minutes, max_participants, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:variant:' || p.n || ':' || v.k),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:product:' || p.n),
    CASE v.k WHEN 1 THEN 'Adult' ELSE 'Child' END,
    ((CASE v.k WHEN 1 THEN (3000 + (p.n * 100)) ELSE (1500 + (p.n * 50)) END)::text || '.00')::numeric(12,2),
    'JPY',
    90,
    8,
    'seed', 'seed'
  FROM generate_series(1, 50) AS p(n), generate_series(1, 2) AS v(k)
  ON CONFLICT (id) DO NOTHING;

  -- ===== Demo users (10) =====
  INSERT INTO users (id, firebase_uid, email, display_name, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:user:demo' || lpad(u.n::text, 2, '0')),
    'demo' || lpad(u.n::text, 2, '0'),
    'demo' || lpad(u.n::text, 2, '0') || '@example.com',
    'Demo User ' || u.n,
    'seed', 'seed'
  FROM generate_series(1, 10) AS u(n)
  ON CONFLICT (id) DO NOTHING;

  -- ===== Reviews (50): one per product from a rotating demo user =====
  INSERT INTO reviews (id, user_id, product_id, rating, title, body, language, status, helpful_count, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:review:' || p.n),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:user:demo' || lpad((1 + (p.n % 10))::text, 2, '0')),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:product:' || p.n),
    3 + (p.n % 3),
    'Great experience #' || p.n,
    'Seeded demo review for product ' || p.n || '. Lorem ipsum dolor sit amet.',
    'ja',
    'PUBLISHED',
    (p.n % 5),
    'seed', 'seed'
  FROM generate_series(1, 50) AS p(n)
  ON CONFLICT (id) DO NOTHING;

  -- Roll up review aggregates so product detail responses are populated.
  INSERT INTO product_review_aggregates (product_id, average_rating, review_count, updated_at)
  SELECT product_id, AVG(rating)::numeric(3,2), COUNT(*)::int, now()
  FROM reviews
  WHERE status='PUBLISHED'
  GROUP BY product_id
  ON CONFLICT (product_id) DO UPDATE
    SET average_rating = EXCLUDED.average_rating,
        review_count   = EXCLUDED.review_count,
        updated_at     = now();

END $$;
