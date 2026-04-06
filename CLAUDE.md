# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Internal study clone of the Asoview product family (8 product apps) built on GCP. The project preserves publicly observable Asoview architecture patterns, substituting AWS with GCP infrastructure. Phase 1 (shared commerce/identity domain core) is implemented; Phase 2 (Asoview! consumer marketplace) is next.

## Architecture

- **Backend**: Java 21 + Spring Boot 4, modular monolith (`commerce-core`) + microservices (`ticketing-service`, `reservation-service`, `ads-service`, `analytics-ingest`)
- **Gateway**: Spring Cloud Gateway (unified API edge)
- **Contracts**: Protocol Buffers as source-of-truth, gRPC internally, REST/JSON externally under `/v1/...`
- **Consumer web apps**: Next.js (asoview-web, gift-web, furusato-web, overseas-web)
- **Operator web apps**: Next.js + Spring Boot/Thymeleaf (heterogeneous by design)
- **Scanner app**: React Native + Expo (Fast-In style)
- **Primary DB**: Cloud Spanner (booking, inventory, entitlements, check-in)
- **Secondary DB**: Cloud SQL for PostgreSQL (admin, CMS, reporting)
- **Cache**: Memorystore Redis (inventory holds, sessions)
- **Search**: OpenSearch on GKE (Japanese full-text)
- **Auth**: Identity Platform (Firebase-compatible)
- **Messaging**: Pub/Sub for domain events, Cloud Tasks for async jobs
- **Analytics**: BigQuery
- **IaC**: Terraform
- **CI/CD**: Cloud Build + Artifact Registry + Argo CD on GKE
- **Runtime**: Single GKE cluster with namespaces: `edge`, `consumer-web`, `operator-web`, `core-services`, `ops-services`, `ads-services`, `data-jobs`, `observability`

## Repository Structure (Target)

```
apps/           # Frontend apps (Next.js, React Native)
services/       # Backend services (Spring Boot)
  gateway/
  commerce-core/    # Modular monolith: catalog, pricing, orders, payments, entitlements, gift, furusato, overseas, reviews/favorites/points
  ticketing-service/
  reservation-service/
  ads-service/
  analytics-ingest/
contracts/      # Proto definitions and OpenAPI specs
  proto/        # Source-of-truth service contracts
  openapi/
libraries/      # Shared code
  java-common/
  frontend-shared/
  design-tokens/
infra/terraform/
db/             # Schema migrations
  spanner/
  postgres/
  seeds/
docs/           # PRD, technical design, implementation plan
```

## Build Tools

- **Monorepo**: Mixed. `bun` for TS workspaces, `Gradle` for JVM services
- **TS packages**: `bun`
- **JVM build**: `Gradle`
- **TS linting/formatting**: Biome
- **JVM linting/formatting**: Spotless + Checkstyle

## Key Design Documents

Read these in order for full context:
1. `docs/PRD.md` - Product requirements (what to build)
2. `docs/technical_design.md` - Architecture decisions (how to build)
3. `docs/implementation_plan.md` - Execution order (when to build)

## Implementation Phases

- **Phase 0**: Repo skeleton, scaffolds, CI/CD, Terraform baseline
- **Phase 1**: Shared domain core (identity, catalog, inventory, orders, payments, entitlements)
- **Phase 2**: Asoview! consumer marketplace end-to-end
- Subsequent phases add operator products, remaining consumer products, analytics, ads, AI

## Domain Model

Core shared abstractions: entitlements (rights granted to users), orders, payments, inventory slots, ticket passes, gift codes, donations, coupons, check-in events. The entitlement is the central cross-product primitive.

## Product Priority Order

Asoview! > UraKata Ticket > UraKata Reservation > Gift > Furusato Nozei > Overseas > AREA GATE > Ads

## Phase 1 Commerce-Core Domain Map

`services/commerce-core` is a modular monolith. Each subpackage under
`com.asoviewclone.commercecore` owns a domain slice and follows the same
controller -> service -> repository layering:

- `identity/`: `User`, `Tenant`, `TenantUser`, `Venue` JPA entities (Cloud SQL). Firebase-backed auth wires into `security/` via `FirebaseTokenFilter` and `SecurityConfig`. Tenant RBAC is enforced by `TenantAccessChecker`.
- `catalog/`: `Category`, `Product`, `ProductVariant` JPA entities (Cloud SQL). Public read endpoints at `/v1/categories` and `/v1/products`.
- `inventory/`: `InventorySlot` and `InventoryHold` on Spanner (authoritative). Holds now carry `product_variant_id`, validated against the caller's requested variant at order creation. Redis is only a cache.
- `orders/`: `Order` + `OrderItem` on Spanner. State machine in `OrderStatus`. `OrderRepository.updateStatusIf` provides compare-and-swap used by cancel and confirm paths.
- `payments/`: `Payment` JPA entity (Cloud SQL). `PaymentServiceImpl.createPaymentIntent` publishes a `PaymentCreatedEvent` on the application event bus; a `@TransactionalEventListener(AFTER_COMMIT)` with `@Retryable` advances the order from PENDING to PAYMENT_PENDING across Spanner. `confirmPayment` uses CAS to transition PAYMENT_PENDING -> PAID.
- `entitlements/`: `Entitlement` and `TicketPass` on Spanner. Created on payment success, idempotent by `(orderItemId)`.

Cross-cutting:

- Spring Data JPA auditing (`@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`) is populated from the application via an `AuditorAware` wired to `SecurityContextHolder`. The shared `AuditFields` embeddable lives in `libraries/java-common`.
- A saga coordinator (`payments/saga/PaymentConfirmationSaga`) owns multi-item payment confirmation and per-step compensation via a Spanner `payment_confirmation_steps` ledger; a scheduled `SagaRecoveryJob` resumes stale PENDING steps.

## Local Dev Environment

Prerequisites: JDK 21 (`brew install openjdk@21`), Docker, `bun`.

```bash
# 1. Start backing services (Postgres 16, Redis 7, Spanner emulator)
docker compose up -d

# 2. Apply Spanner DDL to the local emulator (init container runs on startup)
#    If re-running manually, rerun: docker compose run --rm spanner-init

# 3. Build + test all JVM modules
./gradlew build

# 4. Run commerce-core locally against docker-compose stores
SPRING_PROFILES_ACTIVE=local ./gradlew :services:commerce-core:bootRun
```

Key env vars (local profile defaults in `application-local.yml`):

- `SPANNER_EMULATOR_HOST=localhost:9010`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/asoview`
- `SPRING_DATA_REDIS_HOST=localhost`

Tests use Testcontainers (Postgres, Redis, Spanner emulator via `org.testcontainers:gcloud`) so no running docker-compose is required for `./gradlew test`.
