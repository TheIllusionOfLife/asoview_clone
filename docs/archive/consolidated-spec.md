# Asoview Clone: Consolidated Implementation Spec v1.0

## 1. Executive Summary

This document is the single source of truth for building asoview clone services on Google Cloud Platform. It merges two independent research reports into one actionable spec covering all 8 services, unified domain model, API design, infrastructure, and phased delivery plan.

**Scope:** Functional reproduction of Asoview Inc.'s observable product behavior. Not a pixel-perfect copy. Original branding, dummy data, and distinct visual identity throughout. No scraping, no logo/image copying, no brand confusion.

**Services (all included from initial plan):**

| # | Service | Type | Domain |
|---|---------|------|--------|
| 1 | asoview! | Consumer marketplace | Leisure/experience booking |
| 2 | Urakata Ticket | B2B SaaS | Facility e-ticketing |
| 3 | asoview! Gift | Consumer EC | Experience gift catalog |
| 4 | asoview! Furusato Nozei | Consumer | Hometown tax experience returns |
| 5 | Urakata Reservation | B2B SaaS | Activity/class reservation mgmt |
| 6 | asoview! Overseas | Consumer marketplace | International experiences |
| 7 | Asoview Ads | B2B self-serve | CPC advertising platform |
| 8 | AREA GATE | B2B2C | DMO/tourism content & sales |

## 2. Technical Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Monorepo | bun workspaces | User preference, fast, native TS |
| Frontend | Next.js (per app) | SSR/ISR, React ecosystem |
| Scanner App | React Native / Expo | Fast-In QR scanner, native camera access |
| Backend | Hono on Cloud Run | Lightweight, edge-ready, TypeScript |
| DB | Cloud SQL PostgreSQL 15 | Strong transactions, referential integrity, cost-effective |
| Cache | Memorystore Redis | Inventory holds (TTL=15min), sessions |
| Search | OpenSearch (Managed) | Japanese full-text with kuromoji tokenizer |
| Auth | Firebase Authentication | OAuth2/OIDC, social login (Google, Apple, LINE), Custom Claims for RBAC |
| Payments | Stripe (real) | Stripe Connect for facility payouts, PayPay via Stripe |
| Storage | Cloud Storage + Cloud CDN | Images, assets, tickets |
| Messaging | Pub/Sub | Event-driven async (booking events, analytics, notifications) |
| Analytics | BigQuery (raw + mart) | KPI, funnel, RFM segmentation |
| AI/ML | Vertex AI | Search query rewrite, recommendation, content summarization |
| CI/CD | Cloud Build + Artifact Registry | Docker build, deploy to Cloud Run |
| IaC | Terraform | All GCP resources |
| ORM | Drizzle ORM | Type-safe, PostgreSQL-native, lightweight |
| Validation | Zod | Schema validation shared between frontend and backend |
| Formatting/Linting | Biome | User preference |

## 3. Architecture

### 3.1 GCP Project Structure

Single project for simplicity. Dataset-level separation for analytics.

```
project: asoview-clone
├── Cloud Run services (core-api, operator-api, ads-api, event-ingest)
├── Cloud SQL (PostgreSQL)
├── Memorystore (Redis)
├── OpenSearch (Managed or self-hosted on GCE)
├── Cloud Storage (assets, tickets)
├── Pub/Sub (events)
├── BigQuery (analytics_raw, analytics_mart, ads_raw, ads_mart)
├── Vertex AI (search/recommend/summarize)
├── Firebase Auth
├── Cloud Build + Artifact Registry
├── Secret Manager
├── Cloud CDN + Load Balancer
└── Cloud Monitoring + Logging
```

### 3.2 Service Architecture (Mermaid)

```mermaid
flowchart TB
  subgraph Clients
    CW[Consumer Web<br/>asoview!/Gift/Furusato/Overseas]
    OW[Operator Web<br/>UraKata Admin/AREA GATE]
    SC[Scanner App<br/>React Native/Expo]
  end

  subgraph Edge
    CDN[Cloud CDN]
    FH[Firebase Hosting]
  end

  subgraph Compute["Cloud Run Services"]
    API[core-api<br/>Orders/Entitlements/Tickets/Payments]
    OPAPI[operator-api<br/>Inventory/Check-in/Reservations/Reports]
    ADS[ads-api<br/>Campaigns/Creatives/Impressions/Clicks]
    ETL[event-ingest<br/>Analytics Collection]
  end

  subgraph Data
    PG[(Cloud SQL<br/>PostgreSQL)]
    RD[(Memorystore<br/>Redis)]
    OS[(OpenSearch)]
    GCS[(Cloud Storage)]
    BQ[(BigQuery)]
    SM[Secret Manager]
  end

  subgraph Async
    PS[Pub/Sub]
    CT[Cloud Tasks]
  end

  subgraph AI
    VAI[Vertex AI]
  end

  CW --> FH --> CDN --> API
  OW --> OPAPI
  SC --> OPAPI

  API --> PG
  API --> RD
  API --> OS
  API --> GCS
  OPAPI --> PG
  OPAPI --> RD
  ADS --> PG
  ADS --> GCS

  API --> PS
  OPAPI --> PS
  ADS --> PS
  ETL --> PS --> BQ

  CT --> API
  CT --> OPAPI

  API --> VAI
  OPAPI --> VAI

  API --> SM
  OPAPI --> SM
  ADS --> SM
```

### 3.3 Repository Structure

```
asoview-clone/
├── package.json                  # bun workspace root
├── biome.json
├── turbo.json                    # (optional: task orchestration)
├── apps/
│   ├── consumer-web/             # Next.js: asoview! + Overseas (route groups)
│   ├── gift-web/                 # Next.js: Gift
│   ├── furusato-web/             # Next.js: Furusato Nozei
│   ├── operator-web/             # Next.js: UraKata Admin + AREA GATE + Ads dashboard
│   └── scanner/                  # React Native / Expo: Fast-In QR scanner
├── services/
│   ├── core-api/                 # Hono on Cloud Run
│   ├── operator-api/             # Hono on Cloud Run
│   ├── ads-api/                  # Hono on Cloud Run
│   └── event-ingest/             # Hono on Cloud Run
├── packages/
│   ├── shared/                   # Zod schemas, types, constants shared across apps/services
│   ├── db/                       # Drizzle schema, migrations, seed scripts
│   └── ui/                       # Shared React components (design tokens, primitives)
├── infra/
│   └── terraform/
│       ├── main.tf
│       ├── variables.tf
│       ├── modules/
│       │   ├── cloud-run/
│       │   ├── cloud-sql/
│       │   ├── redis/
│       │   ├── storage/
│       │   ├── pubsub/
│       │   ├── bigquery/
│       │   ├── vertex/
│       │   ├── cdn/
│       │   ├── secrets/
│       │   ├── monitoring/
│       │   └── build/
│       └── environments/
│           ├── dev.tfvars
│           └── prod.tfvars
├── docs/
│   ├── asoview_spec_report.txt
│   ├── deep-research-report.md
│   └── consolidated-spec.md      # This file
└── scripts/
    └── seed/                     # Seed data runners
```

## 4. Domain Model (Entitlement-Centric)

The core insight: all services produce **entitlements** (rights to an experience). Tickets, gift codes, coupons, vouchers are all entitlement subtypes. This unifies the model across 8 services.

### 4.1 ER Diagram

```mermaid
erDiagram
  TENANT ||--o{ TENANT_USER : has
  USER ||--o{ TENANT_USER : joins
  TENANT ||--o{ VENUE : owns
  VENUE ||--o{ PRODUCT : offers
  PRODUCT ||--o{ PRODUCT_VARIANT : has
  PRODUCT ||--o{ PRODUCT_IMAGE : displays
  PRODUCT_VARIANT ||--o{ INVENTORY_SLOT : allocates
  USER ||--o{ ORDER : places
  ORDER ||--o{ ORDER_ITEM : contains
  ORDER ||--o{ PAYMENT : paid_by
  ORDER_ITEM ||--o{ ENTITLEMENT : grants
  ENTITLEMENT ||--o{ TICKET_PASS : materializes
  ENTITLEMENT ||--o{ GIFT_REDEMPTION : consumed_by
  ENTITLEMENT ||--o{ COUPON_REDEMPTION : applied_by
  TICKET_PASS ||--o{ CHECKIN_EVENT : consumed_at
  VENUE ||--o{ STAFF_DEVICE : uses
  VENUE ||--o{ REVIEW : receives
  USER ||--o{ REVIEW : writes
  USER ||--o{ FAVORITE : saves
  USER ||--o{ POINT_TRANSACTION : earns
  TENANT ||--o{ AD_ACCOUNT : runs
  AD_ACCOUNT ||--o{ AD_CAMPAIGN : contains
  AD_CAMPAIGN ||--o{ AD_CREATIVE : uses
  CATEGORY ||--o{ PRODUCT : classifies
  MUNICIPALITY ||--o{ DONATION : receives
  DONATION ||--o{ FURUSATO_COUPON : issues
```

### 4.2 Table Definitions (PostgreSQL)

#### Core (all services)

```sql
-- Multi-tenant root
CREATE TABLE tenants (
  tenant_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_type     TEXT NOT NULL CHECK (tenant_type IN (
    'consumer_platform', 'venue_operator', 'activity_operator',
    'dmo', 'advertiser', 'internal'
  )),
  name            TEXT NOT NULL,
  status          TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
  commission_rate NUMERIC(5,4) NOT NULL DEFAULT 0.15,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Users (consumers + operators unified)
CREATE TABLE users (
  user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email           TEXT NOT NULL UNIQUE,
  email_verified  BOOLEAN NOT NULL DEFAULT false,
  name            TEXT,
  name_kana       TEXT,
  phone           TEXT,
  birth_date      DATE,
  gender          TEXT CHECK (gender IN ('male', 'female', 'other', 'unspecified')),
  prefecture_code TEXT,          -- JIS code (2 chars)
  family_type     TEXT,          -- solo/couple/family/group
  locale          TEXT NOT NULL DEFAULT 'ja-JP',
  membership_rank TEXT NOT NULL DEFAULT 'standard',
  point_balance   INTEGER NOT NULL DEFAULT 0,
  firebase_uid    TEXT UNIQUE,   -- Firebase Auth UID
  status          TEXT NOT NULL DEFAULT 'active',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tenant-user mapping (RBAC)
CREATE TABLE tenant_users (
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id),
  user_id         UUID NOT NULL REFERENCES users(user_id),
  role            TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'staff', 'analyst', 'viewer')),
  mfa_enabled     BOOLEAN NOT NULL DEFAULT false,
  last_login_at   TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, user_id)
);

-- Venues (facilities, shops, activity locations)
CREATE TABLE venues (
  venue_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id),
  venue_type      TEXT NOT NULL CHECK (venue_type IN (
    'theme_park', 'museum', 'spa', 'activity_shop', 'aquarium',
    'zoo', 'garden', 'temple', 'transport', 'other'
  )),
  name            TEXT NOT NULL,
  name_kana       TEXT,
  description     TEXT,
  prefecture_code TEXT,
  city            TEXT,
  address         TEXT,
  lat             DOUBLE PRECISION,
  lng             DOUBLE PRECISION,
  phone           TEXT,
  official_url    TEXT,
  main_image_url  TEXT,
  timezone        TEXT NOT NULL DEFAULT 'Asia/Tokyo',
  status          TEXT NOT NULL DEFAULT 'active',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Categories (hierarchical)
CREATE TABLE categories (
  category_id     TEXT PRIMARY KEY,
  parent_id       TEXT REFERENCES categories(category_id),
  name            TEXT NOT NULL,
  name_en         TEXT,
  icon_url        TEXT,
  sort_order      INTEGER NOT NULL DEFAULT 0
);

-- Products (unified: plans, tickets, gift catalogs, furusato returns, overseas vouchers)
CREATE TABLE products (
  product_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id        UUID REFERENCES venues(venue_id),   -- null for platform-level products
  product_type    TEXT NOT NULL CHECK (product_type IN (
    'activity', 'ticket', 'gift_catalog', 'gift_kit',
    'furusato_coupon', 'furusato_eticket', 'overseas_voucher'
  )),
  category_id     TEXT REFERENCES categories(category_id),
  title           TEXT NOT NULL,
  description     TEXT,
  duration_minutes INTEGER,
  min_people      INTEGER NOT NULL DEFAULT 1,
  max_people      INTEGER,
  includes        TEXT,               -- what's included in price
  excludes        TEXT,               -- what's not included
  cancellation_policy_type TEXT NOT NULL DEFAULT 'relative_days'
    CHECK (cancellation_policy_type IN ('none', 'relative_days', 'provider_defined')),
  cancellation_hours INTEGER DEFAULT 24,
  language_support JSONB DEFAULT '["ja"]'::jsonb,
  area_id         TEXT,               -- domestic area or overseas city
  status          TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'published', 'paused', 'archived')),
  sort_order      INTEGER NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Product variants (pricing tiers: adult/child/senior, etc.)
CREATE TABLE product_variants (
  variant_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
  variant_name    TEXT NOT NULL,       -- e.g., ADULT, CHILD, General
  price_currency  TEXT NOT NULL DEFAULT 'JPY',
  price_amount    INTEGER NOT NULL,    -- tax-inclusive, in smallest unit
  age_min         INTEGER,
  age_max         INTEGER,
  max_quantity_per_order INTEGER,
  sales_channel   TEXT NOT NULL DEFAULT 'platform'
    CHECK (sales_channel IN ('platform', 'direct', 'agent', 'dmo_widget')),
  sort_order      INTEGER NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Product images
CREATE TABLE product_images (
  image_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
  url             TEXT NOT NULL,
  alt             TEXT,
  sort_order      INTEGER NOT NULL DEFAULT 0,
  is_main         BOOLEAN NOT NULL DEFAULT false
);

-- Inventory slots (unified: open tickets + datetime-specified)
CREATE TABLE inventory_slots (
  slot_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  variant_id      UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE CASCADE,
  slot_type       TEXT NOT NULL CHECK (slot_type IN ('open', 'datetime')),
  start_at        TIMESTAMPTZ,        -- null for open type
  end_at          TIMESTAMPTZ,
  capacity_total  INTEGER NOT NULL,
  capacity_reserved INTEGER NOT NULL DEFAULT 0,
  capacity_held   INTEGER NOT NULL DEFAULT 0,  -- temporary holds (Redis TTL=15min)
  sales_deadline_at TIMESTAMPTZ,      -- e.g., overseas "day before 00:00"
  status          TEXT NOT NULL DEFAULT 'on_sale'
    CHECK (status IN ('on_sale', 'sold_out', 'closed')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_slots_variant_start ON inventory_slots (variant_id, start_at);
```

#### Orders & Payments

```sql
CREATE TABLE orders (
  order_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(user_id),
  tenant_id       UUID REFERENCES tenants(tenant_id),
  order_type      TEXT NOT NULL DEFAULT 'purchase'
    CHECK (order_type IN ('purchase', 'reservation_request', 'donation')),
  subtotal        INTEGER NOT NULL,
  discount_amount INTEGER NOT NULL DEFAULT 0,
  point_used      INTEGER NOT NULL DEFAULT 0,
  coupon_code     TEXT,
  total_amount    INTEGER NOT NULL,
  currency        TEXT NOT NULL DEFAULT 'JPY',
  status          TEXT NOT NULL DEFAULT 'pending_payment'
    CHECK (status IN ('pending_payment', 'paid', 'confirmed', 'cancelled', 'refunded', 'expired', 'used')),
  placed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user ON orders (user_id, placed_at DESC);

CREATE TABLE order_items (
  order_item_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
  variant_id      UUID NOT NULL REFERENCES product_variants(variant_id),
  slot_id         UUID REFERENCES inventory_slots(slot_id),
  quantity        INTEGER NOT NULL DEFAULT 1,
  unit_price      INTEGER NOT NULL,
  line_total      INTEGER NOT NULL,
  metadata        JSONB               -- participant info, special requests
);

CREATE TABLE payments (
  payment_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        UUID NOT NULL REFERENCES orders(order_id),
  provider        TEXT NOT NULL CHECK (provider IN ('card', 'paypay', 'paidy', 'convenience', 'bank_transfer')),
  stripe_payment_intent_id TEXT,
  status          TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'authorized', 'captured', 'failed', 'refunded')),
  amount          INTEGER NOT NULL,
  currency        TEXT NOT NULL DEFAULT 'JPY',
  fx_rate_id      UUID,               -- for overseas orders
  captured_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_stripe ON payments (stripe_payment_intent_id) WHERE stripe_payment_intent_id IS NOT NULL;
```

#### Entitlements (the unifying abstraction)

```sql
CREATE TABLE entitlements (
  entitlement_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_item_id   UUID NOT NULL REFERENCES order_items(order_item_id),
  entitlement_type TEXT NOT NULL CHECK (entitlement_type IN (
    'ticket_pass', 'gift_code', 'coupon_balance', 'voucher'
  )),
  status          TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'consumed', 'expired', 'revoked')),
  valid_from      TIMESTAMPTZ,
  valid_to        TIMESTAMPTZ,
  consumption_rules JSONB,            -- e.g., {"staff_only": true, "reentry": false}
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### Tickets & Check-in

```sql
CREATE TABLE ticket_passes (
  ticket_pass_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entitlement_id  UUID NOT NULL REFERENCES entitlements(entitlement_id),
  display_type    TEXT NOT NULL CHECK (display_type IN ('swipe', 'qr')),
  qr_token        TEXT NOT NULL UNIQUE,   -- HMAC-SHA256 signed token
  not_before_at   TIMESTAMPTZ,            -- don't display until this time
  used_at         TIMESTAMPTZ,
  used_by_staff_id UUID,
  expires_at      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE checkin_events (
  checkin_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_pass_id  UUID NOT NULL REFERENCES ticket_passes(ticket_pass_id),
  venue_id        UUID NOT NULL REFERENCES venues(venue_id),
  device_id       UUID,
  staff_id        UUID,
  quantity_used   INTEGER NOT NULL DEFAULT 1,
  result          TEXT NOT NULL CHECK (result IN ('success', 'already_used', 'invalid', 'expired')),
  device_info     TEXT,
  checked_in_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_checkin_events_venue ON checkin_events (venue_id, checked_in_at);

-- Staff scanner devices (Fast-In)
CREATE TABLE staff_devices (
  device_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id),
  device_type     TEXT NOT NULL CHECK (device_type IN ('android', 'ios', 'web_scanner')),
  device_name     TEXT,
  api_key_hash    TEXT NOT NULL,
  last_seen_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### Gift-specific

```sql
CREATE TABLE gift_catalogs (
  gift_catalog_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      UUID NOT NULL REFERENCES products(product_id),
  max_selectable_plans INTEGER,
  catalog_rules   JSONB
);

CREATE TABLE gift_codes (
  gift_code_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entitlement_id  UUID NOT NULL REFERENCES entitlements(entitlement_id),
  code            TEXT NOT NULL UNIQUE,   -- random, non-guessable
  redeem_status   TEXT NOT NULL DEFAULT 'unused'
    CHECK (redeem_status IN ('unused', 'redeemed', 'void')),
  redeemed_at     TIMESTAMPTZ,
  redeemed_by_user_id UUID REFERENCES users(user_id)
);

CREATE TABLE gift_redemptions (
  gift_redemption_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  gift_code_id    UUID NOT NULL REFERENCES gift_codes(gift_code_id),
  redeem_type     TEXT NOT NULL CHECK (redeem_type IN (
    'activity_reservation', 'ticket_exchange', 'kit_shipping'
  )),
  target_order_id UUID REFERENCES orders(order_id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### Furusato Nozei-specific

```sql
CREATE TABLE municipalities (
  municipality_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  prefecture      TEXT NOT NULL,
  name            TEXT NOT NULL,
  code            TEXT                  -- ministry code (optional)
);

CREATE TABLE donations (
  donation_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(user_id),
  municipality_id UUID NOT NULL REFERENCES municipalities(municipality_id),
  amount          INTEGER NOT NULL,
  payment_id      UUID REFERENCES payments(payment_id),
  donated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE furusato_coupons (
  coupon_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  donation_id     UUID NOT NULL REFERENCES donations(donation_id),
  code            TEXT NOT NULL UNIQUE,
  amount_balance  INTEGER NOT NULL,
  region_constraint_municipality_id UUID REFERENCES municipalities(municipality_id),
  expires_at      TIMESTAMPTZ NOT NULL,
  status          TEXT NOT NULL DEFAULT 'active'
    CHECK (status IN ('active', 'used', 'expired', 'returned')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE coupon_redemptions (
  coupon_redemption_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  coupon_id       UUID NOT NULL REFERENCES furusato_coupons(coupon_id),
  order_id        UUID NOT NULL REFERENCES orders(order_id),
  applied_amount  INTEGER NOT NULL,
  returned_amount INTEGER NOT NULL DEFAULT 0,  -- on cancellation
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### Overseas-specific

```sql
CREATE TABLE countries (
  country_code    TEXT PRIMARY KEY,     -- ISO 3166-1 alpha-2
  country_name    TEXT NOT NULL,
  country_name_ja TEXT
);

CREATE TABLE cities (
  city_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  country_code    TEXT NOT NULL REFERENCES countries(country_code),
  city_name       TEXT NOT NULL,
  city_name_ja    TEXT
);

CREATE TABLE fx_rates (
  fx_rate_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  base_currency   TEXT NOT NULL DEFAULT 'JPY',
  quote_currency  TEXT NOT NULL,
  rate            NUMERIC(18,8) NOT NULL,
  as_of           TIMESTAMPTZ NOT NULL
);

CREATE TABLE overseas_suppliers (
  supplier_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            TEXT NOT NULL,
  support_contact TEXT
);
```

#### Ads-specific (Asoview Ads)

```sql
CREATE TABLE ad_accounts (
  ad_account_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(tenant_id),
  billing_profile JSONB,
  status          TEXT NOT NULL DEFAULT 'active',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ad_campaigns (
  campaign_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ad_account_id   UUID NOT NULL REFERENCES ad_accounts(ad_account_id),
  objective       TEXT NOT NULL CHECK (objective IN ('traffic', 'conversion', 'brand')),
  bid_type        TEXT NOT NULL DEFAULT 'cpc',
  daily_budget    INTEGER NOT NULL,       -- in JPY
  status          TEXT NOT NULL DEFAULT 'draft'
    CHECK (status IN ('draft', 'active', 'paused', 'completed')),
  start_at        TIMESTAMPTZ,
  end_at          TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ad_creatives (
  creative_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  campaign_id     UUID NOT NULL REFERENCES ad_campaigns(campaign_id),
  format          TEXT NOT NULL CHECK (format IN ('image', 'native_card')),
  title           TEXT NOT NULL,
  description     TEXT,
  landing_url     TEXT NOT NULL,
  asset_gcs_path  TEXT,
  status          TEXT NOT NULL DEFAULT 'pending_review',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ad_impressions (
  impression_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creative_id     UUID NOT NULL REFERENCES ad_creatives(creative_id),
  user_id         UUID,
  page_context    TEXT,
  rendered_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ad_clicks (
  click_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  impression_id   UUID NOT NULL REFERENCES ad_impressions(impression_id),
  clicked_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### Social features

```sql
CREATE TABLE reviews (
  review_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  venue_id        UUID NOT NULL REFERENCES venues(venue_id),
  user_id         UUID NOT NULL REFERENCES users(user_id),
  order_id        UUID NOT NULL REFERENCES orders(order_id),
  rating          INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
  title           TEXT,
  body            TEXT,
  family_type     TEXT,
  visited_date    DATE,
  is_published    BOOLEAN NOT NULL DEFAULT false,
  helpful_count   INTEGER NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reviews_venue ON reviews (venue_id, created_at DESC);

CREATE TABLE favorites (
  user_id         UUID NOT NULL REFERENCES users(user_id),
  venue_id        UUID NOT NULL REFERENCES venues(venue_id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, venue_id)
);

CREATE TABLE point_transactions (
  tx_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(user_id),
  amount          INTEGER NOT NULL,     -- positive=earn, negative=use
  tx_type         TEXT NOT NULL CHECK (tx_type IN ('earn', 'use', 'expire', 'adjust')),
  order_id        UUID REFERENCES orders(order_id),
  description     TEXT,
  expires_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_point_tx_user ON point_transactions (user_id, created_at DESC);
```

## 5. API Design

### 5.1 Conventions

- REST/JSON, OpenAPI 3.1 spec
- Versioned: `/v1/...`
- Auth: `Authorization: Bearer <Firebase JWT>`
- Operator endpoints: `X-Tenant-Id` header (validated against JWT custom claims)
- All mutations require `X-Request-Id` for idempotency and audit
- Error format: `{ "error": { "code": "TICKET_NOT_AVAILABLE_YET", "message": "..." } }`

### 5.2 Public API (Consumer Web/App)

#### Discovery
- `GET /v1/areas` - Area hierarchy (region > prefecture > city)
- `GET /v1/categories` - Genre tree
- `GET /v1/products?query=&area_id=&category=&type=&date=&price_min=&price_max=&sort=&page=&limit=`
- `GET /v1/products/{product_id}`
- `GET /v1/products/{product_id}/availability?from=&to=` - Slot list with remaining capacity
- `GET /v1/products/{product_id}/reviews?page=&limit=`

#### Cart & Orders
- `POST /v1/carts` -> `{ cart_id }`
- `POST /v1/carts/{cart_id}/items` - Add item (variant_id, slot_id, qty)
- `DELETE /v1/carts/{cart_id}/items/{item_id}`
- `POST /v1/orders` - Create from cart (triggers inventory hold in Redis, TTL=15min)
- `POST /v1/orders/{order_id}/payments` - Initiate payment (Stripe PaymentIntent)
- `POST /v1/payments/webhook` - Stripe webhook (confirms order, creates entitlements)
- `POST /v1/orders/{order_id}/cancel`

#### My Page
- `GET /v1/me` - Profile
- `GET /v1/me/orders`
- `GET /v1/me/tickets` - Ticket passes
- `POST /v1/me/tickets/{ticket_pass_id}/prepare_display`
  - Before `not_before_at`: `409 TICKET_NOT_AVAILABLE_YET`
  - Returns signed short-lived QR payload
- `GET /v1/me/favorites`
- `POST /v1/me/favorites/{venue_id}`
- `DELETE /v1/me/favorites/{venue_id}`
- `GET /v1/me/points`

#### Gift
- `GET /v1/gift/catalogs?budget=&scene=&genre=`
- `GET /v1/gift/catalogs/{catalog_id}`
- `POST /v1/gift/orders` - Purchase gift (returns shareable URL)
- `POST /v1/gift/redeem` - Redeem gift code
  - Already used: `409 GIFT_ALREADY_REDEEMED` (one-time only)
  - Expired: `410 GIFT_EXPIRED`

#### Furusato Nozei
- `GET /v1/furusato/municipalities`
- `GET /v1/furusato/products?municipality_id=&category=`
- `POST /v1/furusato/donations` - Make donation
- `GET /v1/furusato/coupons/{code}` - Check coupon validity
- `POST /v1/furusato/coupons/{code}/apply` - Apply to order
  - 1 coupon per order, no stacking: `409 COUPON_STACKING_NOT_ALLOWED`
  - Region mismatch: `409 COUPON_REGION_MISMATCH`

#### Overseas
- `GET /v1/abroad/countries`
- `GET /v1/abroad/cities?country_code=`
- `GET /v1/abroad/products?country_code=&city_id=&category=`
- `GET /v1/abroad/products/{product_id}`
- `POST /v1/abroad/orders` - Includes FX rate snapshot, cancellation consent record

### 5.3 Operator API (UraKata / AREA GATE / Ads)

#### Venue & Product Management
- `GET /v1/op/venues`
- `POST /v1/op/venues`
- `PATCH /v1/op/venues/{venue_id}`
- `POST /v1/op/products` - Create (draft -> publish)
- `PATCH /v1/op/products/{product_id}`
- `POST /v1/op/products/{product_id}/publish`

#### Inventory Management
- `POST /v1/op/inventory/slots` - Create open/datetime slots
- `PATCH /v1/op/inventory/slots/{slot_id}` - Update capacity (immediate effect)
- `GET /v1/op/inventory?from=&to=` - Calendar view

#### Check-in (Fast-In)
- `POST /v1/op/checkins/scan`
  ```json
  {
    "device_token": "...",
    "ticket_token": "...",
    "quantity": 1,
    "mode": "qr"
  }
  ```
  Response:
  ```json
  {
    "result": "success",
    "checkin_id": "...",
    "ticket_info": { "product_title": "...", "variant_name": "ADULT" },
    "used_at": "2026-04-05T12:34:56+09:00"
  }
  ```
  Error results: `already_used`, `invalid`, `expired`

#### Reservation (UraKata Reservation)
- `POST /v1/op/reservations/request` - Start email verification flow
- `POST /v1/op/reservations/confirm_email` - Verify one-time URL
- `POST /v1/op/reservations/submit` - Submit reservation form
- `POST /v1/op/reservations/{id}/approve` - Confirm reservation
- `POST /v1/op/reservations/{id}/waitlist` - Move to waitlist

#### Reports
- `GET /v1/op/reports/sales?from=&to=&granularity=daily|hourly`
- `GET /v1/op/reports/checkins?from=&to=`
- `GET /v1/op/reports/inventory?from=&to=`

#### Ads
- `POST /v1/op/ads/campaigns`
- `PATCH /v1/op/ads/campaigns/{campaign_id}`
- `POST /v1/op/ads/creatives`
- `GET /v1/op/ads/reports?campaign_id=&from=&to=` - CTR, CPC, CVR, ROAS

#### External Integration
- `POST /v1/op/integrations/webhooks` - Register outbound webhooks
- `POST /v1/op/integrations/stock/push` - Push inventory to external
- `GET /v1/op/integrations/stock/pull` - Pull from external

## 6. Frontend Design System

### 6.1 Design Tokens

```css
:root {
  /* Brand Colors */
  --color-primary:        #FF6B35;
  --color-primary-dark:   #E55A20;
  --color-primary-light:  #FF8C5A;
  --color-secondary:      #2B5FA5;
  --color-accent-green:   #34A853;   /* available / discount */
  --color-accent-red:     #EA4335;   /* sold out / error */
  --color-accent-yellow:  #FBBC04;   /* rating stars */

  /* Text */
  --color-text-primary:   #1A1A1A;
  --color-text-secondary: #555555;
  --color-text-hint:      #999999;
  --color-text-disabled:  #BBBBBB;
  --color-text-inverse:   #FFFFFF;

  /* Backgrounds */
  --color-bg-base:        #F5F5F5;
  --color-surface:        #FFFFFF;
  --color-surface-hover:  #FFF8F5;
  --color-border:         #E0E0E0;
  --color-border-focus:   #FF6B35;

  /* Spacing (8px grid) */
  --space-1: 4px;   --space-2: 8px;   --space-3: 12px;
  --space-4: 16px;  --space-5: 20px;  --space-6: 24px;
  --space-8: 32px;  --space-10: 40px; --space-12: 48px;
  --space-16: 64px;

  /* Typography */
  --font-family: 'Hiragino Kaku Gothic ProN', 'Noto Sans JP', sans-serif;
  --text-xs: 11px;  --text-sm: 13px;  --text-base: 14px;
  --text-md: 16px;  --text-lg: 18px;  --text-xl: 20px;
  --text-2xl: 24px; --text-3xl: 28px; --text-4xl: 32px;
  --line-height: 1.6;  --line-height-tight: 1.3;

  /* Border Radius */
  --radius-sm: 4px;  --radius-md: 8px;  --radius-lg: 12px;
  --radius-xl: 16px; --radius-pill: 9999px;

  /* Shadow */
  --shadow-sm:   0 1px 3px rgba(0,0,0,0.08);
  --shadow-card: 0 2px 8px rgba(0,0,0,0.08);
  --shadow-hover: 0 4px 16px rgba(0,0,0,0.14);
  --shadow-modal: 0 8px 32px rgba(0,0,0,0.20);

  /* Breakpoints */
  --bp-sm: 375px;  --bp-md: 768px;  --bp-lg: 1024px;  --bp-xl: 1280px;
}
```

### 6.2 Grid Rules

- 8px base spacing
- Container: PC 1200px, Tablet 768px, SP 360-430px
- Card padding: 16px, Card gap: 12-16px

### 6.3 Core Components (minimum set)

| Component | Used in | Key behavior |
|-----------|---------|--------------|
| SearchBar | asoview!, Overseas | Keyword + suggest + recent searches |
| FilterDrawer | asoview!, Gift, Furusato | Area, genre, price range, date (SP: bottom sheet) |
| ProductCard | All consumer | Image, title, price, tags, rating |
| PriceBlock | All consumer | Tax-inclusive, fee note, lowest-price guarantee badge |
| TicketDisplay | asoview!, Furusato | Swipe/QR mode toggle. "Staff must process" prominent |
| GiftCodeRedeem | Gift | Code input + one-time-use warning |
| CalendarInventory | Operator | Slot x capacity grid. Inline edit. Conflict lock |
| Scanner | Fast-In | Full-screen result (green=success, red=fail), double-scan prevention, offline queue |
| CampaignBuilder | Ads | Budget, schedule, creative upload, CPC bid |
| ReservationCalendar | Urakata Reservation | Slot x people x availability |

### 6.4 State Matrix (critical screens)

| Screen | State | Expected UI | API |
|--------|-------|-------------|-----|
| Product list | 0 results | "Change search criteria" + popular categories | `GET /products` -> empty |
| Product detail | Sold out | Price shown, CTA = "Sold Out" (disabled) | slot `sold_out` |
| Checkout | Payment failed | Retry / alternate method / contact support | payment `failed` |
| Ticket display | Before event day | "This ticket becomes available on the day" | `409 TICKET_NOT_AVAILABLE_YET` |
| Ticket use | User accidentally used | "Invalid" warning (irreversible) | `used_at` set |
| Gift | Code already used | "This code has already been redeemed" | `409 GIFT_ALREADY_REDEEMED` |
| Furusato coupon | Stacking attempt | "1 coupon per order" | `409 COUPON_STACKING_NOT_ALLOWED` |
| Overseas | Non-cancellable | Checkbox consent required | consent logged |
| Scanner | Double scan | Red "ALREADY USED" + original use time | `already_used` |

## 7. Search (OpenSearch)

### 7.1 Index: products_v1

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "ja_analyzer": {
          "type": "custom",
          "tokenizer": "kuromoji_tokenizer",
          "filter": ["kuromoji_baseform", "lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "product_id":    { "type": "keyword" },
      "title":         { "type": "text", "analyzer": "ja_analyzer" },
      "description":   { "type": "text", "analyzer": "ja_analyzer" },
      "product_type":  { "type": "keyword" },
      "prefecture":    { "type": "keyword" },
      "city":          { "type": "keyword" },
      "categories":    { "type": "keyword" },
      "tags":          { "type": "keyword" },
      "price_from":    { "type": "integer" },
      "rating":        { "type": "float" },
      "review_count":  { "type": "integer" },
      "status":        { "type": "keyword" },
      "location":      { "type": "geo_point" },
      "venue_name":    { "type": "text", "analyzer": "ja_analyzer" }
    }
  }
}
```

## 8. Analytics (BigQuery)

### 8.1 Datasets

- `analytics_raw` - Raw events from all services
- `analytics_mart` - Aggregated KPIs
- `ads_raw` - Impression/click raw events
- `ads_mart` - Campaign performance metrics
- `ops_raw` - Check-in, inventory, incident logs

### 8.2 Event Schema (analytics_raw.events)

| Field | Type | Description |
|-------|------|-------------|
| event_id | STRING | UUID |
| event_ts | TIMESTAMP | Occurrence time |
| user_id | STRING | Logged-in only |
| anonymous_id | STRING | Non-logged-in identifier |
| session_id | STRING | Session |
| page_type | STRING | listing/detail/checkout/mypage/... |
| service | STRING | asoview/gift/furusato/abroad/urakata/ads |
| event_name | STRING | view_item/add_to_cart/purchase/checkin/... |
| props | JSON | Arbitrary (product_id, etc.) |
| user_agent | STRING | UA |
| ip_hash | STRING | Hashed for PII avoidance |

### 8.3 Key KPIs

- **GMV**: `SUM(orders.total_amount) WHERE status='paid'`
- **Purchase CVR**: `purchase_sessions / total_sessions`
- **Gift redemption rate**: `redeemed_codes / issued_codes`
- **Furusato donation total**: `SUM(donations.amount)`
- **Check-in success rate**: `success_checkins / total_scans`
- **Ads**: CTR, CPC actual, CVR, ROAS
- **Repeat rate**: users with 2+ orders / total users

### 8.4 Pub/Sub Topics

| Topic | Publisher | Subscriber(s) |
|-------|-----------|---------------|
| `booking.created` | core-api | event-ingest, notification |
| `booking.confirmed` | core-api (Stripe webhook) | event-ingest, notification |
| `booking.cancelled` | core-api | event-ingest, inventory restore |
| `checkin.scanned` | operator-api | event-ingest |
| `inventory.updated` | operator-api | OpenSearch sync |
| `payment.completed` | core-api | event-ingest, commission calc |
| `analytics.event` | all services | event-ingest -> BigQuery |
| `ad.impression` | ads-api | event-ingest |
| `ad.click` | ads-api | event-ingest |

## 9. Vertex AI Use Cases

### 9.1 Search Query Rewrite

Normalize user input into structured search query with synonyms and filters.

### 9.2 Product Description Summarization

Summarize long descriptions into 3 key points, "good for" / "not for", required items.

### 9.3 Review Summarization

Aggregate reviews into pros/cons/highlights.

### 9.4 Support RAG

FAQ and help content retrieval for ticket usage rules, cancellation policies, etc.

## 10. Seed Data

### 10.1 Scale

- 20 venues (6+ categories: aquarium, zoo, spa, pottery, rafting, garden, museum, temple)
- 50 products (2-3 per venue)
- 100 variants (adult/child/senior per product)
- 30 days of slots (including some sold-out)
- 10 test users (diverse attributes)
- 30 orders (all statuses represented)
- 50 reviews (rating 1-5 distribution)
- 20 point transactions
- 5 gift catalogs
- 5 municipalities with furusato products
- 3 overseas countries, 5 cities, 10 overseas products
- 2 ad accounts, 5 campaigns, 10 creatives

### 10.2 Seed Script Structure

```
packages/db/seeds/
├── 01_categories.ts
├── 02_areas.ts
├── 03_tenants.ts
├── 04_venues.ts
├── 05_products.ts
├── 06_variants.ts
├── 07_slots.ts
├── 08_users.ts
├── 09_orders.ts
├── 10_entitlements.ts
├── 11_reviews.ts
├── 12_points.ts
├── 13_gift.ts
├── 14_furusato.ts
├── 15_overseas.ts
├── 16_ads.ts
└── run_all.ts
```

## 11. Environment Variables (.env.example)

```bash
# ===== GCP =====
GCP_PROJECT_ID=asoview-clone
GCP_REGION=asia-northeast1

# ===== Database =====
DATABASE_URL=postgresql://user:pass@host:5432/asoview
REDIS_URL=redis://10.0.0.10:6379

# ===== Auth (Firebase) =====
FIREBASE_PROJECT_ID=asoview-clone
FIREBASE_API_KEY=
FIREBASE_AUTH_DOMAIN=asoview-clone.firebaseapp.com

# ===== Social Login =====
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
APPLE_CLIENT_ID=
LINE_CHANNEL_ID=
LINE_CHANNEL_SECRET=

# ===== Payments (Stripe) =====
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_CONNECT_CLIENT_ID=

# ===== Search (OpenSearch) =====
OPENSEARCH_ENDPOINT=
OPENSEARCH_USERNAME=
OPENSEARCH_PASSWORD=

# ===== Storage =====
GCS_BUCKET_ASSETS=asoview-clone-assets
GCS_BUCKET_TICKETS=asoview-clone-tickets

# ===== Analytics =====
BQ_DATASET_RAW=analytics_raw
BQ_DATASET_MART=analytics_mart

# ===== ML =====
VERTEX_AI_LOCATION=asia-northeast1

# ===== Maps =====
GOOGLE_MAPS_API_KEY=

# ===== Feature Flags =====
ENABLE_RECOMMENDATION=false
ENABLE_OVERSEAS=false
ENABLE_ADS=false
ENABLE_FURUSATO=false
```

## 12. Delivery Phases

### Phase 1: Core Foundation (Week 1, Days 1-4)

**Goal:** asoview! core booking flow end-to-end + Urakata Ticket check-in

- Monorepo setup (bun workspaces, Biome, Drizzle)
- Terraform: Cloud SQL, Cloud Run, Redis, Storage, Pub/Sub, BigQuery, Secret Manager
- DB migrations (core tables + ticket tables)
- Seed data (venues, products, slots, users)
- core-api: Discovery (areas, categories, products, availability)
- core-api: Cart -> Order -> Payment (Stripe real integration)
- core-api: Entitlement + ticket_pass creation on payment confirmation
- consumer-web: Search -> Detail -> Purchase -> My Page -> Ticket Display
- operator-api: Check-in scan endpoint
- scanner app: QR scan -> result display (React Native/Expo)
- OpenSearch: Index setup + sync + search endpoint

### Phase 2: Extended Services (Week 1, Days 5-7)

**Goal:** Gift, Furusato, UraKata Reservation

- Gift tables + API (catalog, purchase, code generation, redemption)
- gift-web: Browse -> Purchase -> Share URL -> Redeem flow
- Furusato tables + API (municipalities, donations, coupon issuance, apply)
- furusato-web: Municipality -> Donate -> Coupon/Ticket receive -> Use
- UraKata Reservation: Calendar, email verification flow, approve/waitlist
- operator-web: Reservation management UI

### Phase 3: Overseas + Ads + AREA GATE (Week 2, Days 8-10)

**Goal:** International and monetization

- Overseas tables + API (countries, cities, FX rates, suppliers)
- consumer-web: /abroad routes, FX display, cancellation consent
- Ads tables + API (campaigns, creatives, impressions, clicks)
- operator-web: Ads dashboard (create campaign, upload creative, view reports)
- AREA GATE: Widget embed endpoint, multi-language support skeleton

### Phase 4: Analytics + AI + Polish (Week 2, Days 11-14)

**Goal:** Data pipeline, AI features, load testing, quality

- event-ingest service: Collect events -> Pub/Sub -> BigQuery
- BigQuery mart views (GMV, CVR funnel, repeat rate, ads performance)
- Vertex AI: Search query rewrite, product summarization
- Point system + membership rank logic
- Review submission + moderation flow
- Load testing (inventory contention, concurrent check-ins)
- Monitoring + alerting setup
- CI/CD pipeline (Cloud Build)
- GCP credit consumption optimization

## 13. GCP Credit Allocation (¥45,000 / 2 weeks)

| Service | Budget | Purpose |
|---------|--------|---------|
| Cloud SQL | ¥8,000 | PostgreSQL (db-custom-1-3840), always-on staging |
| Cloud Run | ¥7,000 | 4 services, auto-scale, load test spikes |
| Memorystore | ¥3,000 | Redis (Basic, 1GB) |
| Vertex AI | ¥10,000 | Query rewrite, summarization, RAG experiments |
| BigQuery | ¥5,000 | Event streaming, mart queries, ML features |
| Cloud Storage + CDN | ¥3,000 | Assets, ticket QR images, cache validation |
| OpenSearch (GCE) | ¥4,000 | e2-medium instance with kuromoji |
| CI/CD + misc | ¥5,000 | Cloud Build runs, Artifact Registry, networking |

## 14. Legal/Ethics Guardrails

1. **No scraping** of asoview.com. All data is self-created dummy data.
2. **No brand confusion.** Use distinct name, logo, colors for public-facing deployment. Spec colors are for development reference only.
3. **No real facility data.** Seed data uses fictional venues.
4. **Staff-only ticket consumption.** The "staff must process check-in" rule is a core UX/safety pattern, not just a feature. Enforce in both API (permission check) and UI (prominent warning).
5. **Audit logs** on all payment and ads mutations from day 1.
6. **QR Code** is a registered trademark of DENSO WAVE. Include attribution in UI if displaying QR codes.
