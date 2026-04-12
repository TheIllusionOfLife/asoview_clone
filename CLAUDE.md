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

## Pitfall enforcement (PR 3d.5)

Most rules in the sections below are also enforced mechanically by `scripts/checks/*.sh` (Tier 1 shell checks), `services/commerce-core/src/test/java/.../arch/*Rules.java` (Tier 1.5 ArchUnit), and dedicated regression tests (Tier 2). Run `./scripts/checks/run-all.sh` locally before committing; CI runs the same script in the `Lint - Pitfalls` job. Add a fixture pair under `scripts/checks/__fixtures__/` for any new shell check, and a meta-test entry in `test-fixtures.sh`. Do not add new prose-only rules — every recurring pitfall gets a mechanical guard.

| Rule | Mechanism | Location |
|---|---|---|
| Assigned-`@Id` `save()` defers SQL past catch | shell | `scripts/checks/assigned-id-save.sh` |
| NUMERIC money parsed via parseFloat / Number | shell | `scripts/checks/money-parsing.sh` |
| `@Modifying` missing `clearAutomatically=true,flushAutomatically=true` | shell | `scripts/checks/modifying-flush-clear.sh` |
| Playwright `page.route` under `e2e/ssr/` | shell | `scripts/checks/ssr-no-route.sh` |
| `@Modifying` without `@Transactional` (any granularity) | ArchUnit | `arch/JpaTransactionalRules.java` |
| AFTER_COMMIT publisher not `@Transactional` | ArchUnit | `arch/EventPublisherRules.java` |
| `@Component` Filter without `FilterRegistrationBean` suppressor | ArchUnit | `arch/FilterRegistrationRules.java` |
| `@Profile`-restricted bean injected unconditionally | ArchUnit | `arch/ProfileConsumerRules.java` |
| Webhook double-confirm via assigned-`@Id` `save()` regression | runtime | `PaymentWebhookControllerReplayTest` |
| Point ledger insert-first idempotency race | runtime | `PointLedgerIdempotencyTest` |
| Reconciliation job not re-publishing AFTER_COMMIT events | runtime | `OrderPaidEventReconciliationTest` |
| `cart.subtotal` fractional-yen / Math.trunc regression | runtime | `cart.subtotal.test.ts` |
| `apiRequest` retries non-5xx | runtime | `api.retry.test.ts` |
| UTC date math on JST slot dates | runtime | `SlotPicker.jst.test.ts` |
| SSR manifest drift (page loses `force-dynamic`/`revalidate`) | runtime | `ssr-manifest.test.ts` |

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

## Review Pitfalls (learned the hard way on PR #21)

**4 review rounds, ~30 findings.** Backend domain expansion (PayPay, reviews, favorites, points, search-service, wallet, OpenSearch infra). Most findings clustered on 5 themes. Each is reformulated below as a generalizable rule, NOT a one-off concrete case, because most of these were second-instance bugs that the existing rules already covered in spirit but not in letter.

### `@TransactionalEventListener(AFTER_COMMIT)` requires the publisher's method to be `@Transactional` — even if every persistent write is on a different store

PR #18 documented "self-call bypass": calling a `@Transactional` method on the same bean via `this.x(...)` skips the proxy. PR #21 hit the **inverse**: a method that publishes a domain event consumed by `@TransactionalEventListener(AFTER_COMMIT)` must itself be `@Transactional` so the listener has a transaction to hang AFTER_COMMIT off of. Without it, the listener is **silently dropped** — no error, no log, just nothing happens.

Concrete case: `OrderServiceImpl.cancelOrder` published `OrderCancelledEvent` consumed by `PointRefundListener @TransactionalEventListener(AFTER_COMMIT)`. The method had no `@Transactional` (every persistent write is a Spanner CAS, so it "didn't need a JPA transaction"). Result: burned points were never refunded on cancel and the bug was undetectable from logs.

Rule: **any method that publishes a domain event consumed by an AFTER_COMMIT listener MUST be `@Transactional`**, even if it has zero JPA writes. The empty JPA tx is the AFTER_COMMIT hook. Test: the unit/integration test for the publisher must verify the listener fires (mock `ApplicationEventPublisher` or use a Spring test that injects a real listener and asserts the side effect).

### Plain `save()` is not just a status-column problem. The same race kills any read-modify-write on a multi-writer column

PR #18 + #19 documented "plain `save()` on a status column with multiple writers is always a bug." PR #21 hit the **same root cause** on a balance column (`PointBalance.balance`) and a counter (`Review.helpfulCount`). I read the rule, internalized it for status enums only, and missed the generalization.

The actual rule: **read-modify-write via `save()` is broken whenever the column has multiple concurrent writers, regardless of column type.** Status enums, integer counters, money balances, JSON merge fields — all the same race. Last writer wins, intermediate updates are silently lost.

Patterns that ARE safe:
- `@Modifying @Query("UPDATE Foo SET col = :new WHERE id = :id AND col = :expected")` returning row count → CAS retry loop on the caller side.
- `@Modifying @Query("UPDATE Foo SET counter = counter + 1 WHERE id = :id")` → atomic increment, no read needed.
- `INSERT ... ON CONFLICT (key) DO NOTHING` returning row count → idempotency gate when the goal is "exactly once for this key."

Patterns that ARE NOT safe even though they look like they are:
- `findById` → mutate → `save()` (the canonical race; what we keep regressing on).
- `existsBy` → if not present → `save()` (TOCTOU; the second writer hits the unique constraint at flush time, often inside a doomed transaction).

Rule: **never read-modify-write a column that more than one code path can write to.** Use `@Modifying` CAS or atomic increment. The "more than one code path" includes "the same code path running twice concurrently."

### Idempotency goes INSERT-FIRST, not exists-then-insert

The corollary to the above: when you want "exactly once for this `(reason, order_id)` tuple," structure the operation as `INSERT ... ON CONFLICT DO NOTHING` returning row count. **If the row count is 0, return idempotent no-op. If 1, you hold the lock and can proceed.**

PR #21 first attempted "balance CAS first, ledger save second, catch DataIntegrityViolationException as compensation." The compensation ran inside a transaction Spring had already flipped to rollback-only by the very exception it was reacting to. The compensation never persisted. Codex caught it. The fix is to flip the order: ledger insert FIRST as the gate, balance CAS only if the gate said we won.

Rule: **when the goal is "exactly once per key," the uniqueness check and the side-effect MUST be in that order, atomically.** Insert (with `ON CONFLICT DO NOTHING`) is the gate. Anything you do after is fine because you already hold the lock. Anything you do BEFORE risks running in a doomed transaction the moment the duplicate insert lands.

### `@Profile`-restricted beans with unconditional injection crash the excluded profiles

PR #21 wallet module: `WalletDevCertProvider` was `@Profile({"local","test","default"})`. `AppleWalletPassBuilder` injected it via constructor (unconditionally — no `@Autowired(required=false)`, no `Optional<>`). Result: `SPRING_PROFILES_ACTIVE=gke` failed bean wiring at context init and the entire app refused to start.

Rule: **a `@Profile`-restricted bean is acceptable ONLY if every consumer is also `@Profile`-restricted to the same set, OR the consumer takes it as `Optional<X>` / `@Autowired(required=false)`.** Otherwise: drop the `@Profile` annotation, give the bean a sensible fallback for the missing-config case, and let the production deployment override the config.

Apply at code review: search for `@Profile` annotations on every bean and verify the consumer graph.

### `getContentLengthLong()` returns -1 for chunked transfer

Any HTTP filter enforcing a body-size cap that uses `request.getContentLengthLong() > maxBodyBytes` is bypassable by chunked transfer encoding (`Content-Length` absent → returns -1 → -1 > cap is false → request passes through).

Fix options:
1. **Reject `< 0`** if the upstream provider always sends `Content-Length` (Stripe and PayPay do).
2. **Wrap the input stream** with a size-counter that aborts at the cap.

Rule: **any size-cap that reads `getContentLengthLong()` must explicitly handle the `< 0` case.**

### Spring Security `addFilterBefore` + `@Component` filter = double registration

A custom `Filter` annotated `@Component` gets registered TWICE in a Spring Boot servlet stack: once via Spring Security's `addFilterBefore(filterBean, ...)` and once via Spring Boot's automatic servlet-filter registration of every `Filter` `@Component`. The filter runs for every request twice, halving any rate limit and doubling any logging.

Fix: either drop `@Component` and `@Bean` it from the security config, OR keep `@Component` and add a `FilterRegistrationBean<X>` that calls `setEnabled(false)` to suppress the auto-registration.

Rule: **a `Filter` `@Component` that is also passed to `addFilterBefore`/`addFilterAfter` MUST have its servlet auto-registration suppressed via `FilterRegistrationBean.setEnabled(false)`.**

### `EntityManager.save()` with assigned `@Id` defers SQL to commit time — a try-catch around it CAN'T catch the constraint violation

For an entity with `@Id` (no `@GeneratedValue`) — for example a join-table row with `@IdClass` or composite key — `JpaRepository.save(...)` calls `EntityManager.persist(...)` which **defers the actual INSERT to the next flush**. Hibernate's auto-flush in AUTO mode only flushes if a subsequent query touches the same dirty entity's table. So this code does NOT work:

```java
try {
  voteRepository.save(new ReviewHelpfulVote(reviewId, userId)); // persist queued, no SQL
  reviewRepository.incrementHelpfulCount(reviewId); // @Modifying UPDATE on a DIFFERENT table — no auto-flush
} catch (DataIntegrityViolationException dup) {
  // never fires here; the unique-constraint violation throws at commit time, OUTSIDE the try
}
```

Fix: `voteRepository.saveAndFlush(...)` (or explicit `entityManager.flush()`) so the INSERT hits the database INSIDE the try.

Rule: **when expecting `DataIntegrityViolationException` from a save of an entity with assigned `@Id`, you MUST use `saveAndFlush` (or explicitly flush) — `save` alone defers the SQL past your catch block.**

### Cross-store reconciliation jobs MUST re-publish AFTER_COMMIT events

PR #21 had `PaymentReconciliationJob` repairing `Spanner order PAID ↔ Postgres payment SUCCEEDED` divergence. The repair correctly CAS'd the payment row to `SUCCEEDED` but did NOT re-publish `OrderPaidEvent`. Consequence: any order recovered through this path never earned points, never sent the confirmation email, never fired analytics — silent functional regression of the recovery path.

Rule: **any reconciliation job that fixes a divergent state must re-publish the same domain events the happy path emits.** Otherwise recovered orders are functionally second-class. Test: the integration test for the reconciliation job must verify the AFTER_COMMIT listeners fire on a recovered row.

### Public search/list endpoints must hard-filter on visibility status

PR #19 caught this for `GET /products/{id}/availability`. PR #21 caught it again for the new `GET /v1/search` (OpenSearch indexed inactive products and the query had no `status=ACTIVE` filter). Same rule, new code path.

Rule: **any public list/search endpoint that returns a domain entity must hard-filter on visibility (`status='ACTIVE'`, `published=true`, `deleted_at IS NULL`, etc.) inside the query, not in the application layer.** Index documents are equally vulnerable: if you index "all products," your search endpoint is a leak waiting to happen.

### Defense in depth: gateway `permitAll`/`denyAll` only protects external traffic

PR #21 added a `denyAll` matcher on `/v1/search/admin/**` at the gateway. Any in-cluster pod could still reach `search-service:8082/v1/search/admin/reindex` directly via the ClusterIP. Gateway filters protect the external edge; in-cluster traffic needs its own protection.

Rule: **gateway authz is necessary but not sufficient.** Pair every gateway-level access rule with EITHER an in-process auth check on the downstream service OR a NetworkPolicy that restricts ingress to the expected upstream pod label. Default to NetworkPolicy because it's stateless and works for unauthenticated services.

### `NetworkPolicy` on a clustered StatefulSet must allow node-to-node traffic on the transport port

OpenSearch transport port 9300 (Elasticsearch same; Cassandra 7000; Kafka inter-broker 9093). Without an explicit ingress rule allowing pods of the same statefulset to reach EACH OTHER on that port, the cluster never forms — but the failure mode is a slow timeout and a confusing log line, not an obvious error.

Rule: **any NetworkPolicy on a clustered service must include a self-selector ingress rule for the cluster transport port.**

### NUMERIC string columns require BigDecimal, not `Long.parseLong`

PR #21 stored `OrderItem.unitPrice` as `String` (`NUMERIC(12,2).toPlainString()`), so the on-the-wire value is always something like `"1500.00"` — never bare `"1500"`. The new points-earn subtotal calc used `Long.parseLong(item.unitPrice())` which **always** throws `NumberFormatException` on the trailing `.00`. The catch swallowed it, treated the line as 0, and the user never earned any points. Devin caught it. The fix is `new BigDecimal(s).longValueExact()`.

Rule: **`NUMERIC(N,M)` columns serialized as Strings are not parseable as Long.** Use `BigDecimal`. Better: don't serialize money as `String`; use a typed money DTO. Best: avoid silent zero — log loudly if a parse fails so the bug doesn't sit silently in production.

### Recovery / sweep jobs must log errors loudly

A `catch (Exception ignored)` in a recovery sweep job means a permanent stuck row with no operator visibility. PR #21 had this in `OrderServiceImpl.createOrder`'s burn-refund path AND in `PaymentReconciliationJob.computeSubtotalJpy`. Both were caught in review.

Rule: **`catch (...) { /* ignored */ }` is forbidden in any sweep/repair/reconciliation/recovery code path.** Use `log.error(... , ex)` with enough context (id, user, amount) for ops to find the affected row and repair it manually if the sweep can't.

### Other PR #21 review pitfalls

- **`@Profile`-restricted beans crashing the excluded profiles** — covered above.
- **`@ConditionalOnProperty` for provider-specific controllers**: `PayPayWebhookController` was unconditionally registered even when `payments.gateway != paypay`, so a Stripe-only deploy got a controller bound to the wrong gateway bean. Rule: provider-specific controllers must be `@ConditionalOnProperty` on the same property that selects the provider implementation.
- **Bounded vs unbounded in-memory caches**: `WebhookRateLimitFilter` used a plain `ConcurrentHashMap<String, Bucket>` keyed on remote IP. A long tail of unique callers (botnet scan) grows the map without bound. Rule: any in-memory cache keyed on a high-cardinality external attribute (IP, user-agent, header value) must use Caffeine (or equivalent) with `maximumSize` AND `expireAfterAccess`.
- **`@Modifying` JPA queries need `clearAutomatically=true` (and usually `flushAutomatically=true`)**: otherwise the persistence context retains the stale entity and a subsequent `findById` returns the pre-update value, breaking CAS retry loops. Rule: every `@Modifying` query should be `@Modifying(clearAutomatically=true, flushAutomatically=true)` unless you can prove the persistence context is empty.
- **Pre-generate cross-store ids when burning before writing**: the `OrderServiceImpl.createOrder` flow now pre-generates the order id BEFORE the points-burn so the burn ledger row can pin to a stable id, and the Spanner write accepts the pre-generated id. Rule: any write that spans two stores must pre-allocate the foreign-key value before the first write so recovery jobs can join the two sides.
- **Postgres PR-specific metadata in javadoc**: comments like `(PR #21 review N4 from CodeRabbit)` rot. Document the WHY of the rule, not the WHEN of the discovery. References in commit messages and PR descriptions are fine.
- **Always-running k8s `Filter`s on a clustered StatefulSet need a `PodDisruptionBudget`**: otherwise rolling node upgrades drain quorum.
- **`bootstrap.memory_lock=true` requires `IPC_LOCK` capability**: dropping `ALL` capabilities for security and leaving `bootstrap.memory_lock=true` causes a startup warning. Fix: set `memory_lock=false` (cgroup memory limit + JVM Xms==Xmx is sufficient) or add `capabilities.add: ["IPC_LOCK"]`.

## Review Pitfalls (learned the hard way on PR #33)

**13 commits, 5+ review rounds, 8 fix commits for 5 feature commits.** Event pipeline with transactional outbox pattern. The fix-to-feature ratio reveals systematic gaps in pre-review verification. Every finding below was preventable before the first push.

### Async APIs that return void hide delivery failures from callers

`GcpPubSubPublisher.publish()` called `pubSubTemplate.publish()` (returns `CompletableFuture<String>`) but attached only a `.whenComplete()` logging callback and returned `void`. The outbox relay called `markPublished()` immediately after `publish()` returned, before delivery was confirmed. If the async future later failed, the event was permanently lost. Every reviewer (4/4) caught this.

Rule: **when wrapping an async API for a caller that needs delivery confirmation, the wrapper MUST either block on the future (`.get()` / `.join()`) or return the future to the caller.** A `void` return type on a method that delegates to a `CompletableFuture` is always a contract lie. Before writing a wrapper around any async SDK method, answer: "does the caller need to know if this succeeded?" If yes, propagate the result. The outbox relay's try-catch was correctly structured to skip markPublished on exception, but it never received the exception because the interface hid it.

### Adding a Spring Boot starter without testing its auto-config in CI is a guaranteed CI failure

Adding `spring-cloud-gcp-starter-pubsub` to commerce-core activated `GcpPubSubAutoConfiguration` which requires a GCP project ID. This crashed both the `ApplicationTest.contextLoads()` and the Seed Smoke CI job. The fix required excluding both `GcpPubSubAutoConfiguration` and `GcpPubSubReactiveAutoConfiguration`, then manually wiring `PubSubTemplate` with `@ConditionalOnProperty`.

Rule: **every time you add a `spring-cloud-*-starter` or `spring-boot-starter-*` dependency, immediately verify that the existing `ApplicationTest.contextLoads()` still passes locally.** Starters bring auto-configuration that may require credentials, project IDs, or running infrastructure. If the auto-config fails, exclude it in `Application.java` and wire the bean manually with `@ConditionalOnProperty` or `@ConditionalOnBean`. This is the same pattern we use for Spanner and Firestore. Run the test BEFORE committing, not after CI reports the failure.

### Proto field names must match the actual data source, not the aspirational schema

The proto defined `total_amount_jpy` but the data source was `OrderPaidEvent.subtotalJpy`. "Total" implies discounts and fees are included; "subtotal" is the pre-discount sum. The mismatch silently corrupts analytics: queries that filter on "orders over X total" would return wrong results because the field is actually the subtotal.

Rule: **name proto fields and BigQuery columns after the actual data source field, not the conceptual field you wish it were.** If `OrderPaidEvent` carries `subtotalJpy`, the proto field is `subtotal_jpy`. Renaming it to `total` is a semantic change that requires populating it differently, not just renaming the label.

### Domain events must carry all fields their outbox listeners need

`PaymentCreatedEvent` only carried `orderId`. The outbox listener needed `paymentId`, `amountJpy`, and `provider` to populate the proto. Without them, BigQuery got empty strings and zeros for every payment row, making the analytics table useless. The fix required changing the domain event record and the publish site.

Rule: **before writing an outbox listener for a domain event, list every proto field the listener will set. If any field is not on the event record, enrich the event record at the publish site FIRST.** Do not write a listener that produces incomplete protos. Check at design time, not review time.

### Scheduled job quarantine logic must use DB state, not in-memory entity state

`OutboxRelayJob` incremented `attempt_count` in the DB, then checked `event.getAttemptCount() + 1` (the in-memory value loaded at the start of the batch). Concurrent runners loading the same stale base value could both overshoot the max-attempts cap. The fix combined increment and quarantine into a single atomic SQL UPDATE.

Rule: **any decision based on a counter that multiple writers can increment must read the DB value, not the in-memory entity value.** This is a generalization of the "read-modify-write via save() is broken for multi-writer columns" rule from PR #21. The same root cause: the in-memory entity is a snapshot, not the current truth.

### Error-handling code that logs but does not throw is a silent data loss vector

`BigQueryWriterService.insert()` checked `response.hasErrors()`, logged, and returned normally. The subscriber then acked the Pub/Sub message. Data permanently lost. This is the same class as the PR #21 "silent zero" pitfall: any error path that does not propagate the error to the caller is an implicit "success" signal.

Rule: **at system boundaries (Pub/Sub ack/nack, outbox mark-published, payment confirmation), the error path must throw or return a failure signal. Logging is not error handling.** Before writing any "log and continue" catch block, ask: "will the caller treat this as success?" If yes, throw instead.

### Pre-review checklist for outbox / event pipeline code

Before pushing outbox or event pipeline code, verify:
1. **Delivery confirmation**: does the publisher block until the broker acknowledges? (async + void = lost events)
2. **Error propagation**: does every error path throw to the caller? (log-only = silent data loss)
3. **Field completeness**: does the domain event carry all fields the outbox listener needs? (empty proto fields = useless analytics)
4. **Naming accuracy**: do proto/BQ field names match the actual data source? (aspirational names = semantic corruption)
5. **Auto-config compatibility**: does `ApplicationTest.contextLoads()` still pass after adding the new starter? (auto-config + no credentials = CI crash)
6. **Poison message handling**: what happens when a row fails permanently? (no quarantine = head-of-queue blocking)
7. **Concurrent safety**: does the quarantine decision use the DB value? (in-memory entity = stale under concurrency)

## Process Lessons (workflow patterns from PR #33)

PR #33 had a 1.6:1 fix-to-feature commit ratio (8 fixes for 5 features). Three structural workflow problems caused this:

### Run the full CI-equivalent locally before the first push

PR #33's first CI failure was `ApplicationTest.contextLoads()` in analytics-ingest, caused by the Pub/Sub starter auto-config. This was discoverable locally in 10 seconds. The second CI failure was the same issue in commerce-core's Seed Smoke job. Both required a full push-wait-read-fix-push cycle (20+ minutes each).

Rule: **before the first push of any PR, run `./gradlew build --no-daemon` locally (or at minimum `:services:<affected>:test`).** Catching auto-config, missing-bean, and ArchUnit violations locally saves two CI round-trips per issue. The 10 seconds of local verification prevents 40+ minutes of CI feedback loops.

### Trace the async contract from SDK to caller before writing the wrapper

The fire-and-forget bug was the #1 finding across all 4 reviewers. It could have been caught by reading the `PubSubTemplate.publish()` return type (`CompletableFuture<String>`) and asking: "does my caller need to know if this succeeded?" The answer for an outbox relay is always yes. Instead, the wrapper was written with a `.whenComplete()` callback pattern copied from a logging use case, not a delivery-confirmation use case.

Rule: **when wrapping an external SDK method, start by reading the SDK's return type and error contract.** Write the wrapper's signature to match what the CALLER needs, not what the SDK provides. If the caller needs synchronous confirmation, block. If the caller needs async, return the future. Never swallow the result.

### Design-time field audit prevents multi-round proto/event enrichment

PR #33 went through three rounds of proto field fixes: (1) `PaymentCreatedEvent` missing fields, (2) `total_amount_jpy` renamed to `subtotal_jpy`, (3) `optional` keyword discussion. All three were design-time decisions that should have been made before the first commit.

Rule: **before writing proto message definitions, create a field mapping table: proto field -> source field -> data type -> nullable?** Review the table against the actual domain event records. Any field in the proto that has no source field is a design gap. Any proto field whose name differs from the source field is a semantic decision that needs explicit justification.

## Process Lessons (workflow patterns from PR #21)

PR #21 took 4 review rounds. Each round caught a NEW class of bug that the previous round missed AND a regression of a rule from an earlier PR. The escalation is itself the signal: I need stronger pre-review verification, not just more review rounds.

### After a subagent commits, run the full test suite for the affected module

PR #21 spawned 4 subagents in parallel for new domains (payments, reviews/favorites/points, search-service, wallet). At least one subagent (reviews/favorites/points) added new constructor parameters to `PointBalanceRepository` / `ReviewServiceImpl` that broke the existing service tests. The orchestrator declared the subagent done, moved on, and the broken tests only surfaced in the next test run several commits later.

Rule: **every subagent commit MUST be followed by `./gradlew :services:<module>:test` from the orchestrator before moving on.** Subagents own their scope; the orchestrator owns integration. Do not declare a subagent done without verifying the existing test suite still passes.

### Never `git add -A` while subagents are running

PR #21 had two incidents where the orchestrator's `git add -A && git commit` swept in-flight files from a parallel subagent into the wrong commit, forcing the subagent to soft-reset and re-commit. One of those commits ended up with a misleading message (claimed to be "wallet" but contained search-service indexer files).

Rule: **while subagents are running, the orchestrator only `git add` specific paths it owns.** Subagents stage and commit atomically when they finish. `git add -A` is allowed only when the working tree is provably free of subagent work (either no agents are running, or all running agents are in observation/research mode).

### A recovery/sweep job without a test is fiction

PR #21 added `OrphanedDiscountReconciliationJob` and `PaymentReconciliationJob` re-publish behavior. Neither has an integration test. There is no proof that a stranded orphaned discount is actually recovered. "We'll write a test for the recovery path in a follow-up" is the same lie that produced the original bug — the recovery path runs once per process per problem and reading the code is not the same as exercising it.

Rule: **every new `@Scheduled` repair/sweep/reconciliation job lands with at least one integration test that creates the broken state and verifies the sweep recovers it.** Without that test, the job is a comment-shaped wish.

### When implementing a feature with a user-visible end state, write the integration test for the end state FIRST

PR #21's worst bug was the silent overcharge: `pointsToUse=100` burned 100 points and then created the Stripe payment intent for the GROSS amount. The test that would have caught this on the very first run is one line: "create order with `pointsToUse=100`, assert `saved.totalAmount() == gross - 100`." It did not exist because the implementation was written to "subtract points → create discount row → done" without ever asserting the user-visible end state.

Rule: **for any feature with a user-visible end state (money charged, points balance, ticket count, search hit count), the integration test that asserts the end state goes in BEFORE the implementation, even if it currently fails.** TDD-lite: the test is the spec for what "done" means. Implementation that doesn't pass that test is not done.

### Reading rules from CLAUDE.md is necessary but not sufficient — generalize them every time

I read the "plain `save()` on a status column with multiple writers" rule from PR #18 / #19. I applied it to status enums. I missed that the same root cause kills any read-modify-write on any multi-writer column — balances, counters, JSON merge fields. The literal rule was correct; the generalization was missing. So I introduced PR #18's bug AGAIN on a different column type and shipped it past my own self-review.

Rule: **when reading a CLAUDE.md rule, explicitly generalize it.** Ask "what is the root cause this rule prevents?" and "what other code paths in this PR are vulnerable to that root cause?" The literal rule covers one face of the bug; the generalization covers the rest.

### The number of review rounds is itself the signal

PR #20 = 2 review rounds, all clustered on testing edge cases. PR #21 = 4 review rounds, with each round catching a bug introduced by the previous round's fix. The escalation indicates that the fixes are incomplete or the testing surface is too narrow. When a PR is on its 3rd round of review feedback, stop fixing individual findings and re-read the entire diff with the question: "what class of bug am I missing?" Document the class in CLAUDE.md before pushing the next fix.

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
