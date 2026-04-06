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

## Recurring Pitfalls (learned the hard way on PR #18)

These are not generic best practices — each one bit us during the booking/payment correctness work. Read before touching the saga, payments, or Spanner DDL.

### Cross-store consistency: Spanner CAS commits immediately, JPA commits at method exit

`@Transactional` on a method that mixes JPA writes and Spanner CAS calls does NOT make them atomic. Spanner uses its own transactions; the JPA tx commits at method return. A successful Spanner CAS followed by a JPA commit failure leaves Cloud SQL behind Spanner. Patterns we settled on:

- For PENDING → PAYMENT_PENDING: publish a `PaymentCreatedEvent` and have `PaymentCreatedEventListener` (`@TransactionalEventListener(AFTER_COMMIT)` + `@Retryable`) drive the Spanner CAS after the JPA commit. The listener's CAS is idempotent so retries are safe.
- For exhausted retries / divergent state repair: `PaymentReconciliationJob` sweeps PROCESSING payments and CASs the order forward (or marks payment FAILED) based on the order's actual state. Always use `PaymentRepository.updateStatusIf(...)` (a `@Modifying` CAS query), never plain `save()`, to avoid last-writer-wins overwrites.
- For PAYMENT_PENDING → PAID: `confirmPayment` first CASs to an intermediate `CONFIRMING` state so concurrent cancels are blocked between the saga and the final PAID write. The post-saga CAS must live INSIDE the same try-catch that handles saga failures, otherwise a Spanner network blip leaves the order stuck in CONFIRMING permanently.

### `@Transactional` self-call bypasses Spring's proxy

A `@Transactional(propagation = REQUIRES_NEW)` method invoked via `this.method(...)` from another method on the same bean runs in the existing transaction (because the call doesn't go through the proxy). Symptoms: a "FAILED status persisted in a new transaction" pattern silently rolls back with the outer transaction.

Use a programmatic `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` instead. See `PaymentServiceImpl.markPaymentFailedInNewTransaction`.

### Inventory hold confirmation is idempotent — recovery jobs must check sibling state

`InventorySlotRepository.confirmHold` is a no-op when the hold row is already deleted (so concurrent confirmHolds and saga retries don't double-increment `reserved_count`). This idempotency is load-bearing but also dangerous: a `SagaRecoveryJob` that retries a stale FAILED step in isolation will silently mark it CONFIRMED against a deleted hold, with no actual reservation.

Rule: any job that retries a saga step **must** read all sibling steps for the same `payment_id` and refuse to retry if any sibling is COMPENSATED. Pattern: see `SagaRecoveryJob.recoverStalePending`. Same goes for `PaymentConfirmationSaga.confirm` itself — it refuses to resume if any prior step is COMPENSATED.

### Saga compensation marks COMPENSATED only after a successful release

In `PaymentConfirmationSaga`, the compensation loop must mark a step COMPENSATED only after `releaseConfirmedHold` succeeds. If the release throws, mark the step FAILED so `SagaRecoveryJob` (which queries `WHERE status IN ('PENDING','FAILED')`) can retry it. COMPENSATED is terminal and is what the recovery sibling-check looks for.

### Status transitions: prefer CAS over read-then-write

For order/payment/saga-step status changes, use the dedicated CAS methods (`OrderRepository.updateStatusIf`, `PaymentRepository.updateStatusIf`, `PaymentConfirmationStepRepository.updateStatusIf`) and treat a `false`/0 return as a benign concurrent winner. Plain `save()` or `updateStatus()` allows last-writer-wins races.

### Spanner DDL: never edit existing `db/spanner/V*.sql` in place

`SpannerDdlBootstrap.isAlreadyExists` swallows `FAILED_PRECONDITION` so the bootstrap is idempotent on re-runs, but this means **modifying an already-applied V file is silently ignored on existing instances**. Always add a new forward migration (`V<n+1>__add_<column>.sql`) with `ALTER TABLE ... ADD COLUMN ...`. The Cloud Spanner ALTER syntax is forgiving and the bootstrap handles it.

The test schema in `services/commerce-core/src/test/java/.../testutil/SpannerEmulatorConfig.java` is hand-written and can drift from `db/spanner/*.sql`. If you change a Spanner DDL file, mirror the change in `SpannerEmulatorConfig` (or, better, refactor the test config to load from the migration files — Phase 2 candidate).

### Spring Boot 4 / Spring Cloud 2025.x migration gotchas

When bumping `spring-boot` in `gradle/libs.versions.toml`, the following are not auto-applied and produced cryptic build/runtime failures during the 3.x → 4.0.5 upgrade:

- `WebMvcTest`, `AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure.*`. Add `spring-boot-starter-webmvc-test` to test deps.
- `spring-cloud-starter-gateway` was renamed to `spring-cloud-starter-gateway-server-webflux` in Spring Cloud 2025.x.
- `spring-boot-starter-flyway` is now needed explicitly — Flyway autoconfig is no longer pulled by `flyway-core` alone.
- Spring Cloud GCP 8.x ships with `spring-cloud-gcp-starter-data-spanner` but the auto-config is fragile — `Application.java` excludes `GcpSpannerAutoConfiguration`, `SpannerRepositoriesAutoConfiguration`, `SpannerTransactionManagerAutoConfiguration`, and `GcpFirestoreAutoConfiguration` and wires beans manually in `SpannerConfig`.

### Reviewer findings: verify against current code, every time

CodeRabbit, Codex, Devin, and Gemini all flag legitimate bugs and false positives in roughly equal measure on this codebase. Common false-positive patterns we've seen:

- Reviewers assume validations exist that don't (e.g. flagging "hardcoded slot date will become past" when `slot_date` is `STRING(10)` with no temporal validation anywhere).
- Reviewers flag a missing migration when the migration exists in a later V file (e.g. flagging V4 missing the partial unique index when it lives in V5).
- Reviewers flag "event published before commit may rollback" without noticing the listener is `@TransactionalEventListener(AFTER_COMMIT)`.

Always read the actual file before fixing. Document the false-positive verdict in the commit message so future sessions don't re-litigate.

### Spotless runs globally — coordinate when multiple agents touch files

`./gradlew spotlessApply` formats every Java file in the repo, not just changed ones. Two agents (or main + agent) editing files in parallel will produce conflicting formatting changes. When delegating to a subagent that may touch the same files, either send a `SendMessage` to coordinate (skip-list, sequencing) or wait for the agent to complete before editing.

## Review Pitfalls (learned the hard way on PR #19)

12 findings across 4 reviewers (CodeRabbit, Codex, Devin, Gemini) clustered into four themes. Each theme is a rule to apply proactively when writing webhook / async / cross-store code, and a failure mode to test for explicitly.

### Specify every non-happy-path for guard rows written before the operation they protect

Any row inserted as a marker *before* the operation it guards (replay-protection rows, advisory locks, processing flags) must have an explicit cleanup rule for **every** non-success return path — not just the caught-exception path. If the operation returns "don't know yet, retry me later" (unknown provider id, 202), the guard must be removed or the provider's retry hits the duplicate check and is silently dropped forever. Concrete case: `PaymentWebhookController` wrote `processed_webhook_events` first, then returned 202 on unknown provider id without deleting the row; Stripe's next delivery hit the unique-key guard and was ACKed as "duplicate" — the event was lost. Rule: for every `return` in a guarded handler, either the guard is terminal (keep the row) or transient (delete the row). No third category.

### @Transactional entry points on webhook / event / job handlers that self-call into core services

When a webhook, scheduled job, or event listener entry method calls into a `@Transactional` domain-service method on the same bean via `this.x(...)`, the proxy is bypassed and the `@Transactional` on `x` is silently ignored. The entry method must itself be `@Transactional` so the outer proxy opens the transaction and the self-call runs inside it. This is a repeat lesson from PR #18 (transactional self-call bypass), but it bit us again in a new location: `PaymentServiceImpl.confirmByProviderPaymentId` called `this.confirmPayment(...)` without being annotated itself, so the webhook path ran JPA writes without a transaction. Rule: every externally-invoked method that calls a `@Transactional` method on its own bean must itself be `@Transactional`.

### Idempotency keys must propagate to every downstream side effect, not just local dedup

Storing an idempotency key locally is not the same as being idempotent. If the method performs an external side effect (Stripe `PaymentIntent.create`, email send, provisioning call) and a retry of the method after a local-commit failure would re-invoke that side effect, the key **must** be forwarded to the external API (Stripe's `Idempotency-Key` header via `RequestOptions`). Otherwise a retry mints a duplicate intent / sends a duplicate email / provisions a duplicate resource. Concrete case: `PaymentServiceImpl.createPaymentIntent` checked `findByIdempotencyKey` locally but passed no key to `PaymentGateway.createIntent`; a JPA commit failure after Stripe created the intent would retry and create a second intent on Stripe. Rule: every method that takes an idempotency key must either be a pure-local no-side-effect operation, or must pass the key through to every downstream call that has its own idempotency mechanism.

### Out-of-order and terminal-state handling is a design requirement, not an edge case

External event sources (Stripe, Pub/Sub, Kafka) can and will deliver events out of order. Every state-mutating webhook handler needs two explicit guards:

1. **Out-of-order guard**: before writing a new status, check the current status. If the resource is already past the state this event would transition to, no-op and ACK. Concrete case: `payment_intent.payment_failed` arriving after `payment_intent.succeeded` would silently mark a PAID order's payment as FAILED, producing an unreconcilable divergence (`PaymentReconciliationJob` only sweeps PROCESSING). Fix: `failByProviderPaymentId` now refuses to overwrite orders in PAID / CONFIRMING / REFUNDED.
2. **Terminal-vs-transient classification on `ConflictException`**: a webhook handler that catches `ConflictException` must distinguish "retry could succeed later" from "retry can never succeed" (resource is already in a terminal state). Transient → 202, delete replay row. Terminal → 200, keep replay row. Returning 202 on terminal conflicts triggers an infinite retry loop from the provider. Concrete case: a stuck `ConflictException` with payment already SUCCEEDED used to return 202 forever.

### Other review pitfalls from PR #19

- **Plain `save()` on a status column with multiple writers is always a bug**. Use `updateStatusIf` CAS. `markPaymentFailedInNewTransaction` used `save()` and raced with concurrent successful confirmations — last writer wins, silently corrupting state. Rule: if more than one code path can write to a status field, no writer is allowed to use `save()`; every writer must CAS from an expected observed value.
- **`permitAll` in `SecurityConfig` does not stop servlet filters from running.** `/v1/payments/webhooks/**` was `permitAll`, but `FirebaseTokenFilter` still processed any stray `Authorization` header on the request and could return 401 before the webhook signature verifier ran. Rule: custom auth filters must `shouldNotFilter()` public paths explicitly; `permitAll` only covers Spring Security's own authorization, not the filter chain.
- **`new String(bytes)` is a hidden cross-environment bug.** Any byte-to-string conversion at a security boundary (signature verification, hashing, base64, binary protocols) must pass an explicit `Charset`. Stripe webhook signature verification used the JVM default charset and would fail on JVMs that don't default to UTF-8. Rule: `new String(bytes, StandardCharsets.UTF_8)`, always. Never `new String(bytes)`.
- **Public endpoints gate on domain visibility, not just existence.** `GET /products/{id}/availability` checked that the product existed but not that it was ACTIVE, letting callers who know a draft/hidden product id enumerate its schedule and inventory. Rule: when adding a sub-resource endpoint to a publicly-visible parent, copy the parent's visibility check verbatim. Existence is necessary but not sufficient.
- **Filter-priority dispatch shadows existing criteria when a new dimension is added.** `CatalogServiceImpl.listProducts` dispatched between repository methods with an if-else cascade; adding a `venueId` parameter silently dropped the `categoryId` filter when both were set with null status, because the `venueId && !status` branch fired first. Rule: when adding a new filter parameter to a dispatching method, enumerate all combinations of `(oldParam × newParam × status)` in a truth table and confirm each one has a distinct branch, or replace the cascade with a Specification / JPA Criteria query.
- **Fan-out read loops are N+1 until proven otherwise.** `InventoryQueryService.getProductAvailability` called `countActiveHoldQuantity(slotId)` inside a per-slot loop, running one Spanner read per slot per variant. Rule: any loop that calls a repository inside the loop body is an N+1 in review. Structure fan-out reads as "collect all ids → batch-fetch → project in memory". New repository methods for batch reads (`countActiveHoldQuantities(Collection<String>)`) should use `IN UNNEST(@ids)` (Spanner) or `IN (:ids)` (JPA) — not a loop of single-id calls.

## Process Lessons (workflow patterns from PR #18)

Generalizable lessons from 55 commits + 4 reviewer rounds (CodeRabbit, Codex, Devin, Gemini). These are workflow rules, not code patterns.

### Always run `./gradlew spotlessApply` before committing Java

PR #18 had 7 separate `style: apply spotless formatting` commits, each one a CI failure that round-tripped through fix → push → wait → re-fix. Spotless runs in CI but locally is opt-in. Either:

```bash
./gradlew spotlessApply :services:commerce-core:build  # before every commit
```

or install the pre-commit hook once:

```bash
./scripts/install-git-hooks.sh   # writes .git/hooks/pre-commit, runs spotlessApply on staged Java
```

### Coordination logic needs failure-mode TDD before implementation

The saga went through 7+ fix iterations because each review round found a new compensation edge case (current step left FAILED instead of COMPENSATED, sibling COMPENSATED siblings ignored by recovery, post-saga CAS outside try-catch, cross-store rollback divergence, etc.). Before writing any code that does CAS, retry, compensation, or cross-store writes:

1. Enumerate every failure mode: network blip, partial commit, concurrent writer, idempotent no-op against deleted state, JPA-vs-Spanner commit ordering, exhausted retries.
2. Write a test for each failure mode FIRST.
3. Only then write the happy path.

The saga code that survived review is the one where every named failure mode has a named test.

### Version checks: fetch from the actual source

Before claiming a major version is "not GA yet" or "latest is X", fetch the truth:

```bash
curl -s https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml | grep -E '<latest>|<release>'
curl -s https://services.gradle.org/versions/current
```

PR #18 upgraded Spring Boot **twice** (3.5.13 → 4.0.5) because the first attempt assumed 4.x was not GA. It was; Maven Central confirmed it.

### Reviewer findings: ~50% false-positive rate, verify every one

Across CodeRabbit / Codex / Devin / Gemini on PR #18, roughly half of all flagged "potential_issue" findings were false positives. Verifying takes minutes; fixing a false positive wastes a commit and confuses the reviewer next round. Process:

1. Read the actual file at the cited line.
2. Trace the call paths the reviewer assumes exist.
3. If false-positive, document the verdict in the commit message of the next real fix so it doesn't get re-litigated.

Common false-positive patterns we hit (also documented in "Recurring Pitfalls"):
- Reviewer assumes a validation that doesn't exist anywhere.
- Reviewer flags a missing migration that lives in a later V file.
- Reviewer doesn't notice `@TransactionalEventListener(AFTER_COMMIT)` semantics.
- Reviewer says a version doesn't exist when it does (always check Maven Central).

### Don't defer items without explicit user approval

Every "we'll fix it in the next PR" on PR #17 turned into a CRITICAL finding on PR #18 that required a saga compensation rewrite. The user explicitly pushed back: "Postponing these would just make those debts bigger. Upgrade now." Default to addressing findings in the same PR. Only defer when the user explicitly approves AND the deferral is recorded in the PR body's "Post-Merge Recommendations" section.

### Subagent coordination: scope to non-overlapping files

Multiple background agents editing the same files cause merge conflicts and silent reverts (we hit this with PaymentConfirmationSaga.java when an agent rebased over manual edits). When delegating:

- Give each agent a disjoint file scope. List the exact files in the prompt.
- If the main thread plans to edit a file the agent might touch, send a `SendMessage` skip-list before launching.
- Sequence dependent agents instead of running in parallel.

### Conventional commits + small focused commits compound during review

PR #18 has 55 commits and reviewers cited specific commits in their findings. Small, focused commits with conventional messages (`fix(payments): ...`, `test(inventory): ...`) make review feedback easier to address — you can revert one commit instead of unwinding a mega-commit. Avoid bundling unrelated changes.
