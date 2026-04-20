# asoview_clone

Internal study clone of the Asoview product family on GCP. Preserves
publicly observable architecture patterns (modular monolith +
microservices, Spanner for strong-consistency domains, Next.js + Expo
on the client side) while swapping AWS for GCP-native equivalents.

**Live dev site**: https://asoview-clone-dev.duckdns.org

## What's live

| Surface | URL | Status |
|---|---|---|
| asoview! consumer marketplace | https://asoview-clone-dev.duckdns.org | Full funnel: browse, search, checkout (Stripe test mode), orders, favorites, points. AI recommendations, chatbot, popularity-boosted search all enabled via Gemini + BigQuery ranking sync. Installable as a PWA with offline fallback (`docs/adr/003-pwa-hand-rolled-minimal-sw.md`). |
| UraKata Ticket — consumer display | https://asoview-tickets.duckdns.org | Signed-in consumer lists ticket passes, taps one to show QR at the gate. |
| UraKata Reservation — operator UI | https://asoview-operator.duckdns.org | Operators run slot CRUD + reservation approval; SMTP notifications via MailHog in dev. |
| UraKata Ticket — scanner-app | Expo Go / EAS internal build | Camera scan → `POST /v1/op/tickets/redeem` with Firebase-token zero-trust auth + Spanner FGAC enforcement. See `apps/scanner-app/README.md`. |

## Local dev

```bash
docker compose up -d                                      # Postgres, Redis, Spanner emulator
./gradlew :services:commerce-core:bootRun                 # main API on :8081
bun install && cd apps/asoview-web && bun run dev         # web on :3000
```

## Ship-readiness check

One shell command against the dev cluster:

```bash
./scripts/e2e-walkthrough.sh
```

Walks the full **consumer → pass → QR → scan → USED** loop plus the
reservation email path. Exits 0 when every step passes. See the
script header for required env vars.

## Secrets

Dev-cluster secrets flow through Google Secret Manager → External
Secrets Operator → k8s Secrets. Bootstrap a fresh cluster once:

```bash
# 1. Set up GCP project secrets (run-once per env)
export FIREBASE_API_KEY=...
export FIREBASE_APP_ID=...
export FIREBASE_AUTH_DOMAIN=asoview-clone-dev.firebaseapp.com
export FIREBASE_PROJECT_ID=asoview-clone-dev
export GOOGLE_API_KEY=...                      # ai.google.dev
export STRIPE_PUBLISHABLE_KEY=pk_test_...
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...
./scripts/bootstrap-dev-secrets.sh

# 2. Bootstrap Argo CD app-of-apps (run-once)
kubectl apply -f infra/argocd/applications/_root.yaml
```

`scripts/README.md` has the full table of env vars and where each
value comes from.

## Architecture

- **Backend**: Java 21 + Spring Boot 4. Modular monolith
  (`services/commerce-core`) plus microservices: `ticketing-service`,
  `reservation-service`, `ads-service`, `analytics-ingest`,
  `search-service` (+ gateway in `services/gateway`).
- **Contracts**: Protobuf source-of-truth (`contracts/proto/`), gRPC
  internally, REST/JSON externally via the gateway.
- **Spanner**: authoritative for inventory holds, orders,
  entitlements, ticket passes, reservation slots, saga steps.
  FGAC roles enforce append-only audit at the IAM layer.
- **Cloud SQL (Postgres)**: identity, catalog, payments, audit trail.
- **Memorystore Redis**: cache only, never authoritative.
- **OpenSearch** on GKE: full-text search + popularity ranking.
- **BigQuery**: analytics ingest + popularity ranking source.
- **Gemini**: recommendations + chatbot + search-ranking boost.
- **Firebase / Identity Platform**: auth, browserSessionPersistence
  on web, custom claims for operator RBAC.
- **IaC**: Terraform for GCP resources, Argo CD for cluster state,
  Cloud Build for CI/CD.

## Docs

Read in order:

1. [docs/PRD.md](./docs/PRD.md) — what we're building.
2. [docs/technical_design.md](./docs/technical_design.md) — how.
3. [CLAUDE.md](./CLAUDE.md) — contribution conventions, pitfall
   rules, repo layout.
4. [docs/implementation_plan.md](./docs/implementation_plan.md) —
   historical; superseded by the living plan tracked in PR bodies.

## Pitfall enforcement

The repo runs a layered static+runtime pitfall-check suite on every
CI build. See the "Pitfall Enforcement" section in `CLAUDE.md` for
the rule catalog. Run locally before committing:

```bash
./scripts/checks/run-all.sh
```
