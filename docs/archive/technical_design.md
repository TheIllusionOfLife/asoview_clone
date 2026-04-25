# Asoview Clone Technical Design

Status: Draft technical design
Audience: Engineering
Source of truth for product intent: `docs/PRD.md`

## 1. Purpose

This document turns the PRD blueprint into an implementation-oriented design.

Guiding rule:

- Preserve publicly observable Asoview architecture tendencies as much as practical
- Replace AWS-primary infrastructure with GCP equivalents
- Keep product boundaries aligned to the PRD

## 2. Architecture Summary

### 2.1 Intentional Architecture Shape

- Backend: `Java + Spring Boot`
- Service style: `modular monolith + microservices`
- Contracts: `Protocol Buffers` as source-of-truth, `gRPC` internally where useful, `REST` externally
- Runtime: `GKE`
- Edge/API entry: unified gateway layer
- Web frontend: `TypeScript + React + Next.js`
- Operator UI: heterogeneous where useful, including `React` and Spring-rendered admin surfaces
- Scanner app: `React Native + Expo`
- Analytics: `BigQuery`
- Eventing: `Pub/Sub`
- Cache/holds: `Memorystore Redis`
- Object storage: `Cloud Storage`
- CI/CD: `Cloud Build + Artifact Registry + Argo CD on GKE`

### 2.2 Single GCP Project

Confirmed direction:

```text
project: asoview-clone
```

Datasets, clusters, services, secrets, and buckets are separated logically inside the single project.

## 3. Concrete Stack Choices

| Layer | Choice | Notes |
|---|---|---|
| Monorepo | mixed monorepo | `bun` for TS workspaces, `Gradle` for JVM services |
| Consumer web apps | `Next.js` | route-level fidelity for public surfaces |
| Operator web apps | `Next.js` and `Spring Boot + Thymeleaf` | allow heterogeneous UI where it matches public Asoview pattern |
| Scanner app | `React Native + Expo` | Fast-In style dedicated mobile app |
| Gateway | `Spring Cloud Gateway` | unified edge/API entry |
| Core backend | `Java 21 + Spring Boot 3` | main service implementation language |
| Internal contracts | `Protocol Buffers` | schema-first contracts |
| Internal RPC | `gRPC` | selective internal service communication |
| External APIs | `REST/JSON` | consumer/operator public-facing APIs |
| Primary transactional DB | `Cloud Spanner` | strong candidate for booking, inventory, entitlements, check-in domains |
| Secondary relational DB | `Cloud SQL for PostgreSQL` | admin/CMS/reporting/supportive workloads where Spanner is unnecessary |
| Cache / hold store | `Memorystore Redis` | inventory holds, short-lived sessions/tokens |
| Search | `OpenSearch on GKE` | specialized search subsystem, Japanese full-text support |
| Auth | `Identity Platform` | GCP-managed auth, Firebase-compatible client experience |
| Storage | `Cloud Storage + Cloud CDN` | assets, media, generated artifacts |
| Messaging | `Pub/Sub` | domain events and async workflows |
| Jobs/async | `Cloud Tasks` + Pub/Sub | webhooks, retries, background work |
| Analytics | `BigQuery` | raw + mart + ops + ads |
| ETL / reverse ETL | `BigQuery` + scheduled jobs first | evaluate TROCCO-equivalent later if needed |
| AI | `Vertex AI` | lower-priority implementation phase |
| IaC | `Terraform` | infra provisioning |
| Delivery | `Cloud Build`, `Artifact Registry`, `Argo CD` | GKE-oriented delivery |
| JVM build | `Gradle` | standard Spring ecosystem |
| TS package management | `bun` | follows workspace preference |
| Linting / formatting | `Biome` for TS, `Spotless`/`Checkstyle` for JVM | split by language |

## 4. Runtime Topology

### 4.1 GKE-Centered Deployment Model

Use one GKE cluster initially, mirroring the public single-cluster tendency.

Namespaces:

- `edge`
- `consumer-web`
- `operator-web`
- `core-services`
- `ops-services`
- `ads-services`
- `data-jobs`
- `observability`

### 4.2 Service Breakdown

Start with a balanced hybrid:

- one `modular monolith` for core commerce-heavy consumer domains
- separate `microservices` where product or operational boundaries are especially strong

Recommended initial decomposition:

- `gateway`
- `consumer-web-asoview`
- `consumer-web-gift`
- `consumer-web-furusato`
- `consumer-web-overseas`
- `operator-web-urakata-ticket`
- `operator-web-urakata-reservation`
- `operator-web-area-gate`
- `operator-web-ads`
- `scanner-app-api`
- `commerce-core`
- `ticketing-service`
- `reservation-service`
- `ads-service`
- `analytics-ingest`

### 4.3 What Lives in `commerce-core`

`commerce-core` begins as a modular monolith with internal domain modules:

- catalog
- pricing
- orders
- payments
- entitlements
- gift
- furusato
- overseas
- reviews/favorites/points

Modules are separated by package/domain boundaries and Proto contracts.

### 4.4 What Lives Outside the Modular Monolith

These become separate services from the start:

- `gateway`
- `ticketing-service`
- `reservation-service`
- `ads-service`
- `analytics-ingest`

Reason:

- they have clearer product/operational boundaries
- they map well to the public service decomposition
- they isolate check-in, reservation workflow, and ad delivery concerns

## 5. Repository Structure

```text
asoview-clone/
├── apps/
│   ├── asoview-web/
│   ├── gift-web/
│   ├── furusato-web/
│   ├── overseas-web/
│   ├── urakata-ticket-web/
│   ├── urakata-reservation-web/
│   ├── area-gate-web/
│   ├── ads-web/
│   └── scanner-app/
├── services/
│   ├── gateway/
│   ├── commerce-core/
│   ├── ticketing-service/
│   ├── reservation-service/
│   ├── ads-service/
│   └── analytics-ingest/
├── contracts/
│   ├── proto/
│   └── openapi/
├── libraries/
│   ├── java-common/
│   ├── frontend-shared/
│   └── design-tokens/
├── infra/
│   └── terraform/
├── db/
│   ├── spanner/
│   ├── postgres/
│   └── seeds/
└── docs/
    ├── PRD.md
    └── technical_design.md
```

## 6. Contracts and API Shape

### 6.1 Contract Source of Truth

Define service contracts in `contracts/proto`.

Usage:

- internal JVM services: generated gRPC and DTOs
- gateway: translates public REST into internal service calls
- frontend type generation: generated TS models where useful

### 6.2 External API Shape

Public APIs remain REST/JSON under `/v1/...`.

Examples:

- `GET /v1/areas`
- `GET /v1/categories`
- `GET /v1/products`
- `GET /v1/products/{product_id}`
- `POST /v1/orders`
- `POST /v1/orders/{order_id}/payments`
- `GET /v1/me/orders`
- `GET /v1/me/tickets`
- `POST /v1/gift/redeem`
- `POST /v1/furusato/donations`
- `POST /v1/op/checkins/scan`
- `POST /v1/op/reservations/request`
- `POST /v1/op/ads/campaigns`

### 6.3 Internal Contract Domains

Proto package suggestions:

- `catalog.v1`
- `inventory.v1`
- `orders.v1`
- `payments.v1`
- `entitlements.v1`
- `tickets.v1`
- `gift.v1`
- `furusato.v1`
- `overseas.v1`
- `reservation.v1`
- `ads.v1`
- `analytics.v1`

## 7. Frontend Implementation Strategy

### 7.1 Consumer Web

Use `Next.js` for:

- `asoview-web`
- `gift-web`
- `furusato-web`
- `overseas-web`

Goals:

- SSR/ISR for SEO-sensitive pages
- route fidelity to real product surfaces
- shared React component primitives where appropriate

### 7.2 Operator Web

Use a mixed operator UI approach:

- `urakata-ticket-web`: Spring-rendered admin pages are allowed where operational simplicity matters
- `urakata-reservation-web`: Spring-rendered and/or React hybrid allowed
- `area-gate-web`: React/Next.js preferred
- `ads-web`: React/Next.js preferred

This intentionally preserves the public signal that operator/admin surfaces are not purely one-stack React apps.

### 7.3 Scanner App

Use `React Native + Expo`.

Capabilities:

- QR scan
- token validation request
- success/failure full-screen response
- device authentication
- optional offline queue for transient network loss

## 8. Persistence Design

### 8.1 Cloud Spanner Domains

Use `Cloud Spanner` for the most contention-sensitive, correctness-critical domains:

- inventory slots
- bookings / orders
- entitlements
- ticket passes
- check-in events

Why:

- aligns with public newer-system direction
- strong fit for concurrent booking and ticket validation

### 8.2 Cloud SQL Domains

Use `Cloud SQL for PostgreSQL` for supporting relational workloads that do not need Spanner semantics:

- CMS-like content management
- some admin/reporting support tables
- ads configuration if kept isolated
- internal tooling/support workflows

### 8.3 Redis Domains

Use `Memorystore Redis` for:

- inventory holds with TTL
- short-lived checkout/session state
- anti-duplicate request protections where appropriate

### 8.4 Search Store

Use `OpenSearch on GKE` for:

- keyword search
- Japanese tokenization
- faceted filtering
- ranking fields

Initial index focus:

- products
- venues
- categories

## 9. Core Domain Model

### 9.1 Shared Entities

- `users`
- `tenants`
- `tenant_users`
- `venues`
- `categories`
- `products`
- `product_variants`
- `inventory_slots`
- `orders`
- `order_items`
- `payments`
- `entitlements`
- `ticket_passes`
- `gift_codes`
- `gift_redemptions`
- `municipalities`
- `donations`
- `furusato_coupons`
- `coupon_redemptions`
- `countries`
- `cities`
- `fx_rates`
- `overseas_suppliers`
- `staff_devices`
- `checkin_events`
- `ad_accounts`
- `ad_campaigns`
- `ad_creatives`
- `ad_impressions`
- `ad_clicks`
- `reviews`
- `favorites`
- `point_transactions`

### 9.2 Entity Placement

- core commerce/ticketing entities: Spanner
- supportive/admin entities: Cloud SQL unless contention profile suggests Spanner
- analytics facts: BigQuery

## 10. Identity and Access

### 10.1 Consumer Identity

Use `Identity Platform` with Firebase-compatible flows.

Login methods:

- email/password
- Google
- Apple
- LINE

### 10.2 Operator Identity

Same identity foundation, plus tenant-aware claims and RBAC.

Roles:

- `owner`
- `admin`
- `staff`
- `analyst`
- `viewer`

### 10.3 API Access

- browser/app -> REST with bearer token
- internal service -> gRPC and/or signed service identity
- scanner app -> device-bound token + staff auth

## 11. Payments

### 11.1 Payment Providers

- `Stripe` for card-first payment flow
- `PayPay` as explicit secondary payment method

### 11.2 Payment Semantics

- create order
- create payment intent
- hold inventory
- confirm via webhook/callback
- finalize entitlements after payment success

### 11.3 Payout / Settlement

Facility/operator payout design belongs in implementation planning after initial commerce flows, but the data model should leave room for settlement records from the beginning.

## 12. Search and AI

### 12.1 Search Phase 1

Implement:

- keyword
- area
- category
- date

### 12.2 Search Phase 2+

Implement later:

- ranking improvements
- recommendation
- query rewrite
- summarization
- support RAG

### 12.3 Vertex AI Usage

Keep in scope for later phases:

- query rewrite
- product description summarization
- review summarization
- help/support retrieval augmentation

## 13. Analytics and Data Platform

### 13.1 BigQuery Datasets

- `analytics_raw`
- `analytics_mart`
- `ops_raw`
- `ads_raw`
- `ads_mart`

### 13.2 Required Event Streams

- booking created
- booking confirmed
- booking cancelled
- payment completed
- check-in scanned
- inventory updated
- ad impression
- ad click
- analytics page events

### 13.3 Data Platform Direction

Preserve public Asoview-style data importance:

- BigQuery as analytics core
- scheduled marts for KPI/reporting
- integration-ready structure for future BI or reverse-ETL tooling

## 14. Delivery and Deployment

### 14.1 Delivery Tooling

- source in GitHub
- images to Artifact Registry
- builds in Cloud Build
- deployments to GKE
- GitOps via Argo CD on GKE

### 14.2 Environments

- `dev`
- `staging`
- `prod`

Logical separation in one GCP project through namespaces, datasets, service names, and secrets.

## 15. Quality and QA

### 15.1 QA Positioning

QA is a first-class concern, not an afterthought.

Minimum test layers:

- unit tests
- service contract tests
- repository/query tests
- E2E tests for main consumer flows
- operator flow tests
- scanner validation tests

### 15.2 Critical Scenarios

- concurrent booking attempt
- inventory hold timeout
- duplicate payment webhook
- duplicate scan / already-used ticket
- before-valid-time ticket display
- gift already redeemed
- furusato coupon stacking violation
- reservation request / waitlist / approve flow

## 16. Seed Data

Initial seed targets:

- 20+ venues
- 50+ products
- 100+ variants
- 30 days of slots
- 10+ users
- 30+ orders
- 50+ reviews
- 5+ gift catalogs
- 5+ municipalities
- 10+ overseas products
- 5+ ad campaigns

## 17. Intentional Deviations From Public Asoview Reality

Allowed deviations:

- GCP instead of AWS-primary infrastructure
- GCP-managed auth instead of non-GCP auth stack where needed
- GCP-native analytics/storage/messaging substitutions

Disallowed deviations:

- replacing Java/Spring Boot backend shape with convenience-first Node backend
- collapsing separate products into one generic app
- dropping schema-first contract design
- flattening the operator/scanner product split

## 18. Next Engineering Step

Use this document to:

1. choose exact service/module boundaries
2. finalize Spanner vs Cloud SQL ownership by table/domain
3. define Proto contracts
4. define external REST surface
5. create repo scaffolding
6. begin implementation planning by product track
