# asoview_clone

Internal study clone of the Asoview product family, implemented on Google Cloud Platform while preserving publicly observable Asoview product and architecture patterns as much as practical.

## Docs

- [PRD](./docs/PRD.md)
- [Technical Design](./docs/technical_design.md)
- [Implementation Plan](./docs/implementation_plan.md)

## Document Roles

- `PRD.md`
  - Defines what we are building
  - Product scope, fidelity goals, product boundaries, target behaviors

- `technical_design.md`
  - Defines how we intend to build it
  - Architecture shape, stack direction, service boundaries, persistence direction

- `implementation_plan.md`
  - Defines execution order
  - Phases, dependencies, deliverables, and handoff expectations for implementation sessions

## Current Direction

- Product family in scope:
  - `Asoview!`
  - `Asoview! Gift`
  - `Asoview! Furusato Nozei`
  - `Asoview! Overseas`
  - `UraKata Ticket`
  - `UraKata Reservation`
  - `AREA GATE`
  - `Asoview Ads`

- Architecture direction:
  - `Java + Spring Boot`
  - `modular monolith + microservices`
  - `Protocol Buffers / gRPC`
  - `GKE`
  - `TypeScript + React + Next.js` for web
  - `React Native + Expo` for the scanner app
  - `Cloud Spanner` and `Cloud SQL` as a mixed persistence strategy

## Suggested Reading Order

1. `docs/PRD.md`
2. `docs/technical_design.md`
3. `docs/implementation_plan.md`
