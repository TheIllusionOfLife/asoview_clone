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

## Pitfall Enforcement

Rules below are also enforced mechanically by `scripts/checks/*.sh` (Tier 1 shell checks), `services/commerce-core/src/test/java/.../arch/*Rules.java` (Tier 1.5 ArchUnit), and dedicated regression tests (Tier 2). Run `./scripts/checks/run-all.sh` locally before committing; CI runs the same script in the `Lint - Pitfalls` job. Add a fixture pair under `scripts/checks/__fixtures__/` for any new shell check, and a meta-test entry in `test-fixtures.sh`. Do not add new prose-only rules: every recurring pitfall gets a mechanical guard.

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

## Pitfalls: JPA / Hibernate

- **`save()` is a race for any multi-writer column.** Not just status enums: counters, balances, JSON merge fields all suffer the same last-writer-wins race. `findById` -> mutate -> `save()` is the canonical race. `existsBy` -> `save()` is TOCTOU. Use `@Modifying` CAS (`WHERE col = :expected`) or atomic increment (`SET counter = counter + 1`). The "more than one code path" includes the same code path running twice concurrently.
- **Assigned-`@Id` `save()` defers SQL past your catch block.** `persist()` queues the INSERT; it only flushes on commit or when a query touches the same table. A `try { save(...) } catch (DataIntegrityViolationException)` never fires. Fix: `saveAndFlush()`. *(Enforced: shell check)*
- **`@Modifying` needs `clearAutomatically=true, flushAutomatically=true`.** Otherwise the persistence context retains stale entities, breaking CAS retry loops. *(Enforced: shell check)*
- **Fan-out read loops are N+1.** Any repository call inside a loop body: collect IDs -> batch-fetch with `IN UNNEST(@ids)` (Spanner) or `IN (:ids)` (JPA) -> project in memory.
- **Filter-priority dispatch:** When adding a new filter parameter to an if-else cascade, enumerate all `(param x param x status)` combinations in a truth table, or replace with Specification / JPA Criteria.

## Pitfalls: Spring Transactions

- **Self-call bypasses the proxy.** `this.method()` from the same bean skips `@Transactional`. Use `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`. This applies equally to service methods AND webhook/job entry points that call `@Transactional` methods on themselves: every externally-invoked method that calls a `@Transactional` method on its own bean must itself be `@Transactional`.
- **`@TransactionalEventListener(AFTER_COMMIT)` requires the publisher to be `@Transactional`.** Even with zero JPA writes. Without a JPA tx, the AFTER_COMMIT listener is silently dropped (no error, no log). The empty JPA tx IS the hook. *(Enforced: ArchUnit)*
- **Cross-store JPA + Spanner is never atomic.** Spanner CAS commits immediately; JPA commits at method return. Patterns: (1) Publish domain event, AFTER_COMMIT + `@Retryable` drives Spanner CAS after JPA commit. (2) Reconciliation job sweeps divergent state. (3) For PAYMENT_PENDING -> PAID: CAS to intermediate CONFIRMING first; post-saga CAS must be INSIDE the same try-catch as saga failure handling.

## Pitfalls: Concurrency / CAS / Idempotency

- **Status transitions: CAS, not read-then-write.** Use `updateStatusIf(id, expected, new)` returning row count. Treat 0 as benign concurrent winner.
- **Idempotency: INSERT-FIRST.** `INSERT ... ON CONFLICT DO NOTHING` returning row count is the gate. If 0, no-op. If 1, you hold the lock and proceed with side-effects. Never exists-then-insert (TOCTOU). Never side-effect-then-insert (doomed transaction on duplicate).
- **Idempotency keys must propagate to every downstream side effect.** Local dedup is not enough. Forward the key to external APIs (Stripe `Idempotency-Key` header, etc.) so retries after local-commit failure don't create duplicates externally.
- **Pre-generate cross-store IDs before the first write** so recovery jobs can join the two sides by a stable foreign key.
- **Saga-specific:** (1) Recovery jobs must read all sibling steps and refuse to retry if any is COMPENSATED. (2) Mark step COMPENSATED only after successful release; if release throws, mark FAILED for retry. (3) Idempotent `confirmHold` against a deleted hold is a silent no-op: recovery must check siblings.

## Pitfalls: Webhooks / External Events

- **Guard rows: every `return` path must keep (terminal) or delete (transient) the row.** No third category. A 202 "retry later" that doesn't delete the guard silently drops all future retries from the provider.
- **Out-of-order guard:** Before writing a new status, check current status. If already past target state, no-op and ACK.
- **Terminal vs transient `ConflictException`:** Terminal (resource in final state) -> 200, keep replay row. Transient (retry could succeed) -> 202, delete replay row. Returning 202 on terminal = infinite retry loop.
- **`permitAll` does not stop servlet filters.** Custom auth filters must `shouldNotFilter()` public paths explicitly.
- **`new String(bytes)` -> always `new String(bytes, StandardCharsets.UTF_8)`** at security boundaries (signature verification, hashing, base64).
- **`getContentLengthLong()` returns -1 for chunked transfer.** Any size-cap filter must handle `< 0` explicitly (reject or wrap the input stream with a counter).

## Pitfalls: Event Pipeline / Outbox

- **Async void = lost events.** Wrapping a `CompletableFuture`-returning SDK method with a `void` return hides delivery failures. Block (`.get()`/`.join()`) or return the future to the caller.
- **Logging is not error handling.** At system boundaries (Pub/Sub ack/nack, outbox mark-published), error paths must throw or return failure. A log-and-return-normally path silently acks/confirms.
- **Domain events must carry all fields their outbox listeners need.** Audit proto fields against event record fields before writing the listener.
- **Proto/BQ field names must match the actual data source field**, not the aspirational schema. `OrderPaidEvent.subtotalJpy` -> proto field `subtotal_jpy`, not `total_amount_jpy`.
- **Quarantine decisions must use DB state, not in-memory entity state.** Combine increment and quarantine into a single atomic SQL UPDATE.
- **Reconciliation jobs must re-publish the same domain events the happy path emits.** Otherwise recovered rows miss points, emails, analytics.
- **`catch (...) { /* ignored */ }` is forbidden in sweep/repair/recovery code.** Use `log.error(... , ex)` with enough context (id, user, amount) for ops.

### Pre-push checklist for outbox / event pipeline code

1. Does the publisher block until the broker acknowledges?
2. Does every error path throw to the caller?
3. Does the domain event carry all fields the outbox listener needs?
4. Do proto/BQ field names match actual data source fields?
5. Does `ApplicationTest.contextLoads()` still pass after adding the new starter?
6. What happens when a row fails permanently? (quarantine?)
7. Does the quarantine decision use the DB value?

## Pitfalls: Spring Boot / DI

- **`@Profile`-restricted beans:** Every consumer must also be `@Profile`-restricted to the same set, or take `Optional<X>` / `@Autowired(required=false)`. *(Enforced: ArchUnit)*
- **Provider-specific controllers** must be `@ConditionalOnProperty` on the same property that selects the provider implementation.
- **Filter `@Component` + `addFilterBefore` = double registration.** Suppress via `FilterRegistrationBean.setEnabled(false)`. *(Enforced: ArchUnit)*
- **New starter dependency -> run `ApplicationTest.contextLoads()` locally before committing.** Starters bring auto-config that may require credentials. Exclude in `Application.java`, wire manually with `@ConditionalOnProperty`.
- **Bounded in-memory caches only.** Any cache keyed on high-cardinality external attribute (IP, user-agent) must use Caffeine with `maximumSize` + `expireAfterAccess`.
- **Spring Boot 4 / Spring Cloud 2025.x:** `WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure.*`; gateway starter renamed to `spring-cloud-starter-gateway-server-webflux`; `spring-boot-starter-flyway` now required explicitly; GCP Spanner auto-config excluded manually in `Application.java` (see `SpannerConfig`).

## Pitfalls: K8s / Infrastructure

- **Gateway authz is necessary but not sufficient.** In-cluster pods bypass the gateway. Pair with NetworkPolicy or in-process auth on the downstream service.
- **NetworkPolicy on clustered StatefulSet must allow self-selector ingress** on the transport port (OpenSearch 9300, Cassandra 7000, Kafka 9093).
- **PodDisruptionBudget** for always-running clustered StatefulSets; otherwise rolling upgrades drain quorum.
- **`bootstrap.memory_lock=true` requires `IPC_LOCK` capability.** If dropping `ALL` capabilities, either add `IPC_LOCK` or set `memory_lock=false`.

## Pitfalls: General

- **Public endpoints must hard-filter on visibility status** (`status='ACTIVE'`, `published=true`, `deleted_at IS NULL`) inside the query, not in the application layer. Applies to REST endpoints AND search index queries.
- **`NUMERIC(N,M)` string columns require `BigDecimal`, not `Long.parseLong`.** Values like `"1500.00"` always throw `NumberFormatException`. Never silently default to zero on parse failure.

## Pitfalls: Spanner DDL

- **Never edit existing `db/spanner/V*.sql` files.** Bootstrap swallows `FAILED_PRECONDITION`, so edits are silently ignored. Always add `V<n+1>__add_<column>.sql`.
- **Mirror DDL changes in `SpannerEmulatorConfig.java`** (test schema is hand-written and can drift).

## Process Rules

- **Run `./gradlew build` locally before the first push.** Catches auto-config, missing-bean, and ArchUnit violations. Saves 40+ min of CI round-trips.
- **Failure-mode TDD for coordination logic.** Before writing CAS / retry / compensation / cross-store code: enumerate every failure mode, write a test for each FIRST, then implement.
- **Integration test for user-visible end states goes in BEFORE implementation** (money charged, points balance, ticket count, search hit count). The test is the spec for "done."
- **Every `@Scheduled` recovery/sweep job lands with an integration test** that creates broken state and verifies recovery.
- **After a subagent commits, run the full test suite** (`./gradlew :services:<module>:test`) before declaring the subagent done.
- **Never `git add -A` while subagents are running.** Stage specific owned paths only.
- **Subagent coordination: disjoint file scopes.** List exact files in the prompt. Send skip-lists via `SendMessage`. Sequence dependent agents.
- **Spotless runs globally.** Two agents editing files in parallel produce conflicting formatting. Coordinate or sequence.
- **Reviewer findings: ~50% false-positive rate.** Always read the actual file before fixing. Document false-positive verdicts in commit messages. Common FP patterns: assumed validations that don't exist, flagged missing migrations that live in later V files, missed AFTER_COMMIT semantics.
- **When reading a CLAUDE.md rule, generalize it.** Ask: "what root cause does this prevent?" and "what other code paths in this PR are vulnerable?"
- **3rd review round = stop fixing individual findings.** Re-read the entire diff for the class of bug you're missing.
