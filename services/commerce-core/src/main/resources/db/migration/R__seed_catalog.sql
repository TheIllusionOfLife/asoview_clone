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
  -- Deterministically spread: product N maps to venue[1 + N % 8] and category[1 + N % 4].
  -- Product UUIDs are derived from the integer N only (uuid_generate_v5 on
  -- 'asoview-clone:product:<N>'), so downstream fixtures that hardcode these
  -- UUIDs stay stable: scripts/seeds/bigquery/004_seed_product_venue_mapping.sql
  -- and e2e tests that pin specific product ids must not break on this rewrite.
  --
  -- Titles are realistic placeholder names grouped by category so filter and
  -- CJK-search tests resolve meaningfully (e.g. `q=温泉` hits the culture
  -- hot-spring entry via the Japanese translation).
  INSERT INTO products (id, tenant_id, venue_id, category_id, title, description, image_url, status, translations, created_by, updated_by)
  SELECT
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:product:' || t.n),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:tenant:default'),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:venue:' || t.venue_slug),
    uuid_generate_v5(uuid_ns_oid(), 'asoview-clone:category:' || t.category_slug),
    t.title_en,
    'A ' || t.category_slug || ' experience in ' || t.venue_name_en || ' — ' || t.title_en || '. Operated by AsoClone demo.',
    'https://images.unsplash.com/photo-' || t.image_id || '?w=1200&sig=' || t.n,
    'ACTIVE',
    jsonb_build_object(
      'ja', jsonb_build_object(
        'name', t.title_ja,
        'description', t.venue_name_ja || 'で楽しむ' || t.title_ja || '。AsoCloneのデモデータです。'
      ),
      'en', jsonb_build_object(
        'name', t.title_en,
        'description', 'A ' || t.category_slug || ' experience in ' || t.venue_name_en || ' — ' || t.title_en || '.'
      )
    ),
    'seed', 'seed'
  FROM (
    SELECT
      s.n,
      (ARRAY['tokyo','yokohama','kyoto','osaka','sapporo','fukuoka','okinawa','nagoya'])[1 + (s.n % 8)] AS venue_slug,
      (ARRAY['Tokyo','Yokohama','Kyoto','Osaka','Sapporo','Fukuoka','Okinawa','Nagoya'])[1 + (s.n % 8)] AS venue_name_en,
      (ARRAY['東京','横浜','京都','大阪','札幌','福岡','沖縄','名古屋'])[1 + (s.n % 8)] AS venue_name_ja,
      (ARRAY['outdoor','indoor','food','culture'])[1 + (s.n % 4)] AS category_slug,
      CASE (s.n % 4)
        WHEN 1 THEN (ARRAY[
          'Pottery Studio A','Escape Room B','Art Gallery C','Bowling Alley D',
          'VR Arena E','Planetarium F','Trampoline Park G','Climbing Gym H',
          'Arcade Lounge I','Board Game Cafe J','Karaoke Night K','Museum Tour L','Aquarium M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        WHEN 2 THEN (ARRAY[
          'Sushi Workshop A','Ramen Tour B','Sake Tasting C','Cafe Hopping D',
          'Street Food Walk E','Wagyu Dinner F','Tea Pairing G','Wine Tasting H',
          'Bakery Class I','Cooking Class J','Izakaya Crawl K','Chocolate Atelier L','Whisky Bar M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        WHEN 3 THEN (ARRAY[
          'Tea Ceremony A','Calligraphy Class B','Kimono Experience C','Samurai Lesson D',
          'Ninja Training E','Temple Visit F','Shrine Tour G','Hot Spring Retreat H',
          'Geisha Evening I','Taiko Drum Workshop J','Ikebana Session K','Kabuki Show L','Noh Theater M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        ELSE (ARRAY[
          'Rafting Adventure A','Hiking Trail B','Kayak Tour C','Cycling Route D',
          'Camping Retreat E','Surfing Lesson F','Climbing Crag G','SUP Session H',
          'Paragliding Course I','Fishing Trip J','Zipline Forest K','Horseback Ride L','Canyoning M'
        ])[1 + (((s.n - 1) / 4) % 13)]
      END AS title_en,
      CASE (s.n % 4)
        WHEN 1 THEN (ARRAY[
          '陶芸工房 A','脱出ゲーム B','アートギャラリー C','ボウリング D',
          'VRアリーナ E','プラネタリウム F','トランポリン G','クライミングジム H',
          'アーケード I','ボードゲームカフェ J','カラオケ K','博物館ツアー L','水族館 M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        WHEN 2 THEN (ARRAY[
          '寿司体験 A','ラーメンツアー B','日本酒試飲 C','カフェ巡り D',
          '食べ歩き E','和牛ディナー F','お茶体験 G','ワインテイスティング H',
          'ベーカリー教室 I','料理教室 J','居酒屋巡り K','ショコラトリー L','ウイスキーバー M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        WHEN 3 THEN (ARRAY[
          '茶道体験 A','書道教室 B','着物体験 C','侍体験 D',
          '忍者修行 E','お寺巡り F','神社ツアー G','温泉リトリート H',
          '芸者の夕べ I','太鼓体験 J','生け花 K','歌舞伎鑑賞 L','能楽堂 M'
        ])[1 + (((s.n - 1) / 4) % 13)]
        ELSE (ARRAY[
          'ラフティング体験 A','ハイキング B','カヤックツアー C','サイクリング D',
          'キャンプ体験 E','サーフィン F','クライミング G','SUP体験 H',
          'パラグライダー I','フィッシング J','ジップライン K','乗馬体験 L','キャニオニング M'
        ])[1 + (((s.n - 1) / 4) % 13)]
      END AS title_ja,
      CASE (s.n % 4)
        WHEN 1 THEN '1533174072545-7a4b6ad7a6c3'
        WHEN 2 THEN '1504674900247-0877df9cc836'
        WHEN 3 THEN '1528164344705-47542687000d'
        ELSE        '1551632811-561732d1e306'
      END AS image_id
    FROM generate_series(1, 50) AS s(n)
  ) AS t
  -- Upsert (not DO NOTHING) so a rerun after edits to titles / translations /
  -- image URLs actually propagates. Product IDs are UUID-stable by design, so
  -- every rerun would otherwise hit the conflict branch and leave the old
  -- "Demo Experience #N" rows in place forever. The mutable columns are
  -- title / description / image_url / translations; venue / category / status
  -- stay fixed.
  ON CONFLICT (id) DO UPDATE
    SET title = EXCLUDED.title,
        description = EXCLUDED.description,
        image_url = EXCLUDED.image_url,
        translations = EXCLUDED.translations,
        updated_at = now(),
        updated_by = 'seed';

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
