# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Internal study clone of the Asoview product family (8 product apps) built on GCP. The project preserves publicly observable Asoview architecture patterns, substituting AWS with GCP infrastructure. Currently in planning/design phase with no implementation code yet.

## Architecture

- **Backend**: Java 21 + Spring Boot 3, modular monolith (`commerce-core`) + microservices (`ticketing-service`, `reservation-service`, `ads-service`, `analytics-ingest`)
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
