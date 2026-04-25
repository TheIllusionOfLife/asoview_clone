# Asoview Clone Implementation Plan

Status: Draft implementation plan
Audience: Engineering
Depends on:

- `docs/PRD.md`
- `docs/technical_design.md`

## 1. Purpose

This document defines the execution order for implementing the Asoview clone.

It answers:

- what to build first
- what each phase must deliver
- what dependencies must be completed before the next phase
- what future implementation sessions should treat as already decided

## 2. Planning Principles

- All eight products are in scope
- Priority affects sequencing, not inclusion
- Preserve public Asoview product and architecture patterns as much as practical
- GCP is the primary intentional infrastructure deviation
- Prefer building shared primitives once, then applying them across product apps
- Keep each phase deliverable and testable on its own

## 3. Delivery Tracks

Implementation work should be thought of as five parallel-but-dependent tracks:

1. platform foundations
2. consumer commerce
3. operator products
4. analytics and ads
5. polish and AI

## 4. Phase Overview

### Phase 0: Foundation Decisions and Scaffolding

Goal:

- establish repo skeleton, contracts, environments, and service boundaries without building product features yet

Deliverables:

- repo structure aligned with `docs/technical_design.md`
- Java/Spring Boot service scaffolds
- Next.js app scaffolds for each product app
- React Native / Expo scanner app scaffold
- Proto contract repository structure
- GKE/Terraform baseline
- CI/CD baseline
- code quality baseline for Java and TypeScript

Exit criteria:

- all core apps and services compile
- all CI checks run successfully
- local/dev environment bootstraps cleanly

### Phase 1: Shared Domain Core

Goal:

- implement the shared commerce and identity foundations that all products depend on

Deliverables:

- identity and RBAC foundation
- tenant and venue model
- category and product model
- variant model
- inventory slot model
- order and payment model
- entitlement model
- ticket pass model
- Redis-based hold behavior
- initial gateway and REST surface
- initial Spanner and Cloud SQL schema rollout

Exit criteria:

- a user can browse seeded products and create a valid order
- payment success path creates correct downstream rights
- concurrent inventory behavior is protected

### Phase 2: Asoview! Core Consumer Marketplace

Goal:

- make the main marketplace usable end to end

Deliverables:

- area/category discovery
- listing and detail pages
- basic search
- booking/purchase flow
- mypage order history
- ticket display rules
- review/favorite/point foundations

Exit criteria:

- complete `Asoview!` happy path works end to end
- invalid ticket display timing is enforced
- key consumer flows have E2E coverage

### Phase 3: UraKata Ticket

Goal:

- implement ticket-operator workflows and check-in

Deliverables:

- venue/product/ticket-type management
- open and datetime inventory management
- direct ticket sale support
- dedicated scanner validation API
- scanner app QR flow
- check-in logging
- sales and check-in reporting basics

Exit criteria:

- ticket purchased in consumer flow can be validated in scanner flow
- duplicate scan and invalid scan behavior is correct
- operator-side ticket configuration is usable

### Phase 4: UraKata Reservation

Goal:

- implement reservation SaaS plus linked consumer reservation flow

Deliverables:

- reservation inventory/calendar
- reservation request flow
- email verification flow
- waitlist/pending/approve states
- operator reservation board
- customer/reservation management basics

Exit criteria:

- operator can configure reservation inventory
- consumer can complete request-based reservation flow
- operator can approve and manage requests

### Phase 5: Gift

Goal:

- implement gift purchase and redemption end to end

Deliverables:

- gift storefront
- gift checkout
- shareable digital gift flow
- recipient receive/redeem flow
- redemption conversion to reservation/ticket/kit path
- gift status and expiry handling

Exit criteria:

- buyer can purchase and share gift
- recipient can redeem only once
- redemption creates the right downstream state

### Phase 6: Furusato Nozei

Goal:

- implement donation-driven issuance and municipality operations

Deliverables:

- municipality discovery
- donation flow
- immediate coupon/e-ticket issuance
- coupon constraints and region rules
- ticket presentation where applicable
- municipality/admin operations basics

Exit criteria:

- donation leads to valid issued benefit
- coupon misuse rules are enforced
- municipality-side operational workflows are represented

### Phase 7: Overseas

Goal:

- implement overseas-specific catalog and voucher behavior

Deliverables:

- country/city discovery
- overseas detail page fields
- multilingual/supplier-aware content handling
- FX-aware purchase path
- voucher delivery

Exit criteria:

- overseas order flow works end to end
- required restrictions and acknowledgements are enforced

### Phase 8: AREA GATE

Goal:

- implement tourism/DMO product app and official-site distribution flow

Deliverables:

- AREA GATE product app shell
- tourism content/product management
- commerce integration with official-site entry
- partner analytics basics
- multilingual support

Exit criteria:

- AREA GATE works as a distinct product app
- partner-facing booking flow can be exercised end to end

### Phase 9: Ads

Goal:

- implement the advertiser workflow and CPC reporting

Deliverables:

- advertiser account setup
- campaign creation
- creative management
- serving surfaces
- impression/click tracking
- CPC reporting basics

Exit criteria:

- advertiser can create a campaign and observe delivery metrics
- impression/click data flows into analytics correctly

### Phase 10: Analytics, QA Hardening, and AI

Goal:

- harden the platform and implement lower-priority but in-scope intelligence features

Deliverables:

- complete event taxonomy
- BigQuery marts
- operational dashboards
- deeper QA automation
- recommendation/search improvement experiments
- Vertex AI integrations

Exit criteria:

- shared analytics are reliable across products
- QA coverage exists for all critical flows
- AI features are additive and do not destabilize core flows

## 5. Cross-Phase Dependencies

### Hard dependencies

- `Phase 0` before everything
- `Phase 1` before all product phases
- `Phase 2` before `Phase 5` and most of `Phase 6`, because Gift and Furusato reuse core consumer commerce patterns
- `Phase 3` before scanner hardening and ticket-specific operator analytics
- `Phase 4` before full UraKata Reservation rollout
- `Phase 10` comes after all core product flows exist

### Soft dependencies

- `Phase 5` and `Phase 6` can overlap after shared commerce is stable
- `Phase 7` can overlap with later parts of `Phase 6`
- `Phase 8` can begin once shared commerce and partner-facing auth/integration primitives are ready
- `Phase 9` can begin once shared account, analytics, and serving surfaces exist

## 6. First Implementation Sessions

The next coding sessions should not jump directly into random feature work.

Recommended near-term session order:

1. scaffold repo structure and toolchains
2. scaffold GKE/Terraform baseline
3. define Proto package structure
4. define initial Spanner and Cloud SQL ownership by domain
5. implement shared identity/tenant/product primitives
6. implement order/payment/entitlement core
7. implement marketplace happy path

## 7. Session Guardrails

Future implementation sessions should treat these as already decided:

- Java/Spring Boot backend direction
- modular monolith + microservices architecture
- GKE runtime direction
- Protocol Buffers / gRPC internal contract direction
- separate product apps
- dedicated React Native / Expo scanner app
- GCP as the only infrastructure platform

Sessions may still decide:

- exact module/package layout inside each service
- exact Proto file partitioning
- exact table-by-table Spanner vs Cloud SQL placement
- exact operator UI split between React and Spring-rendered pages
- exact testing libraries and project conventions

## 8. Definition of Done By Layer

### Product phase done means:

- happy path implemented
- critical edge cases implemented
- core QA coverage exists
- required analytics events emitted
- docs updated if boundaries changed

### Shared-platform phase done means:

- contract defined
- persistence defined
- observability exists
- retry/idempotency rules implemented where relevant

## 9. Risks To Watch

- over-splitting services too early
- under-modeling the shared entitlement core
- building generic marketplace abstractions that erase product-specific behavior
- treating search/AI as too early a priority
- over-simplifying operator UI when public Asoview behavior is more heterogeneous
- unresolved Spanner vs Cloud SQL domain placement

## 10. Recommended Next Move

Begin implementation with `Phase 0`, then `Phase 1`.

Do not start product-specific coding before:

- repo skeleton is stable
- contracts folder exists
- infra baseline exists
- persistence direction is outlined
- gateway approach is defined
