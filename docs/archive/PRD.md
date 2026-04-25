# Asoview Clone Product Blueprint

Status: Draft PRD blueprint
Audience: Product + Engineering
Purpose: High-fidelity product requirements document for an internal-use Asoview clone built on GCP

## 0. Reading Rules

This document uses three evidence levels throughout:

- `[Observed]`: Publicly observable product behavior or public positioning of a service
- `[Inferred]`: Behavior or requirement derived from multiple observed sources or from the minimum needed to reproduce the product faithfully
- `[Assumption]`: A clone-project choice, not a claim about Asoview's real internal implementation

This document is the product blueprint, not `technical_design.md`.

- It should define what we are cloning
- It should define how faithful the clone should be
- It should define product boundaries, shared capabilities, and target behaviors
- It should not prematurely lock the full app stack

## 1. Project Intent

- `[Assumption]` This project is an internal-use-only study clone of Asoview's public product family
- `[Assumption]` The target is exact where publicly observable and carefully inferred where not observable
- `[Assumption]` Sensitive datasets, real business entities, proprietary assets, and real user data are excluded
- `[Assumption]` GCP is the mandatory platform
- `[Assumption]` The clone should preserve publicly observable Asoview architecture tendencies as much as possible, with GCP replacing AWS-side infrastructure choices
- `[Assumption]` We will create two source documents:
  - PRD / product blueprint: this document
  - technical design document: `technical_design.md`

## 2. Fidelity Goal

### 2.1 Clone Standard

- `[Assumption]` The clone should reproduce the real products as faithfully as possible for internal study
- `[Assumption]` Publicly visible route structure, page hierarchy, information architecture, interaction flow, and user-state behavior should match the real products where they can be observed
- `[Assumption]` Where exact details are not directly observable, the clone should make the most plausible product-faithful inference rather than falling back to generic marketplace conventions

### 2.2 What Must Match

- Top-level product surfaces
- Navigation shape
- Major user journeys
- Cross-page information architecture
- Booking, redemption, donation, and check-in semantics
- Operator workflows and role boundaries
- Product-specific constraints and edge cases

### 2.3 What Must Not Be Reused

- `[Assumption]` Real logos
- `[Assumption]` Real copyrighted images
- `[Assumption]` Real business/customer datasets
- `[Assumption]` Any hidden or non-public proprietary data

## 3. Product Family Scope

### 3.1 Products In Scope From Start

- `Asoview!`
- `Asoview! Gift`
- `Asoview! Furusato Nozei`
- `Asoview! Overseas`
- `UraKata Ticket`
- `UraKata Reservation`
- `AREA GATE`
- `Asoview Ads`

### 3.2 Product Boundary Model

- `[Observed]` The real Asoview service family appears as multiple product surfaces, not one monolithic app
- `[Observed]` Gift, Furusato, Overseas, and other surfaces are publicly presented as distinct experiences
- `[Observed]` AREA GATE is publicly positioned as a separate tourism/DMO product
- `[Inferred]` The clone should model these as separate product apps that share selected platform modules

### 3.3 Delivery Priority

Everything is in scope. Priority affects implementation order, not whether a feature belongs in the clone.

Priority order:

1. `Asoview!`
2. `UraKata Ticket`
3. `UraKata Reservation`
4. `Gift`
5. `Furusato Nozei`
6. `Overseas`
7. `AREA GATE`
8. `Ads`

## 4. Product App Structure

### 4.1 Separate Product Apps

- `[Assumption]` `Asoview!` is its own product app
- `[Assumption]` `Gift` is its own product app
- `[Assumption]` `Furusato Nozei` is its own product app
- `[Assumption]` `Overseas` is its own product app
- `[Assumption]` `UraKata Ticket` is its own product app
- `[Assumption]` `UraKata Reservation` is its own product app
- `[Assumption]` `AREA GATE` is its own product app
- `[Assumption]` `Ads` is its own product app

### 4.2 Shared Platform Modules

- Identity and authentication
- Consumer account primitives
- Operator tenancy and RBAC
- Catalog primitives
- Inventory and availability
- Orders and payments
- Entitlements
- Tickets and vouchers
- Analytics and eventing
- Notifications
- Audit logging

## 5. Observed Architecture Signals

### 5.1 Public Signals We Should Follow

- `[Observed]` Backend systems are strongly associated with `Java + Spring Boot`
- `[Observed]` Architecture is publicly described in `modular monolith + microservices` terms
- `[Observed]` A unified API/gateway layer is part of the public architecture story
- `[Observed]` `DDD` and explicit domain modeling are part of the public engineering vocabulary
- `[Observed]` Contract-first/API-schema-oriented practices are publicly visible through `Protocol Buffers / gRPC`
- `[Observed]` Web frontend is strongly associated with `TypeScript + React + Next.js`
- `[Observed]` Operator/admin UI reality is heterogeneous and publicly includes both `React`-based and `Thymeleaf`-based surfaces
- `[Observed]` `Fast-In` is publicly associated with `React Native + Expo`
- `[Observed]` Consumer native mobile publicly exists with `Swift / SwiftUI` and `Kotlin / Jetpack Compose`
- `[Observed]` Infrastructure is strongly associated with `AWS + Kubernetes/EKS`
- `[Observed]` Public materials describe a `single-cluster` operational pattern for most applications on EKS
- `[Observed]` Search/data infrastructure publicly includes systems such as `Elasticsearch`, `BigQuery`, `TROCCO`, and BI tooling
- `[Observed]` Newer systems publicly mention `Cloud Spanner`

### 5.2 Clone Interpretation

- `[Inferred]` The clone should preserve these architecture tendencies wherever practical
- `[Assumption]` The main intentional architecture difference is cloud choice: `GCP` instead of AWS-primary infrastructure
- `[Assumption]` We should prefer a GCP substitution that preserves the architectural shape, not a convenience-first redesign
- `[Assumption]` The clone should preserve the public pattern of `modular monolith + microservices` rather than forcing an all-monolith or all-microservices design dogma
- `[Assumption]` The clone should preserve schema-first/API-contract thinking rather than ad hoc endpoint design

### 5.3 Mobile Scope Interpretation

- `[Observed]` Fast-In is a dedicated scanner product/app
- `[Observed]` Consumer native mobile exists publicly, but this PRD focuses on the web-visible product family and the dedicated scanner app
- `[Assumption]` Consumer native mobile apps are not part of the initial clone scope for this PRD
- `[Assumption]` The dedicated scanner app should preserve the publicly visible Fast-In mobile-app character

## 6. Shared Identity Model

### 6.1 Product-Specific Surfaces, Shared Identity

- `[Observed]` Product surfaces are distinct
- `[Observed]` Gift redemption behavior indicates shared login/registration concepts with Asoview identity
- `[Inferred]` The clone should use one shared identity foundation with product-specific login entry points, account surfaces, and mypage contexts

### 6.2 Identity Requirements

- `[Assumption]` One shared identity platform across the product family
- `[Assumption]` Product-specific login and account entry should exist where the real products expose them
- `[Assumption]` Cross-product entitlements should not be exposed in one generic universal mypage unless there is strong evidence of that behavior in the real products

## 7. Core Product Principle

### 7.1 Entitlement-Centered Commerce

- `[Inferred]` The most stable shared abstraction across the product family is the `entitlement`, meaning a right granted to a user or recipient to consume, redeem, apply, or present something

This must cover:

- ticket rights
- reservation rights
- gift redemption rights
- coupon balances
- overseas voucher rights

### 7.2 Why This Matters

- `[Inferred]` It allows `Asoview!`, `Gift`, `Furusato`, `Overseas`, and `UraKata` products to share core primitives while preserving separate user experiences

## 8. Product Requirements By App

### 8.1 Asoview!

#### Product Goal

Allow consumers to discover activities and tickets, purchase or reserve them, manage them afterward, and use digital tickets on-site.

#### Required Experience

- `[Observed]` Search by area, category, and keyword
- `[Observed]` Listing pages and detail pages
- `[Observed]` Reservation/purchase flows
- `[Observed]` Reservation management / mypage behavior
- `[Observed]` Ticket display and on-site usage behavior
- `[Observed]` Some tickets may be hidden until a valid date or time
- `[Inferred]` Inventory hold and concurrency controls are mandatory to reproduce behavior reliably

#### Complete Product Scope

- Search and discovery
- Listing and detail pages
- Booking and purchase
- MyPage and post-purchase management
- Ticket display
- Review-related surfaces
- Favorite-related surfaces
- Point-related surfaces

#### Architecture Fit

- `[Observed]` This product belongs to the large-scale reservation/payment/inventory domain highlighted in public engineering materials
- `[Inferred]` Its clone should therefore assume an architecture consistent with Java/Spring Boot-based service boundaries, strong transactional modeling, and contract-oriented API design

#### Search Scope

- `[Assumption]` Initial implementation priority is basic search only:
  - keyword
  - area
  - category
  - date
- `[Assumption]` Advanced ranking, recommendation, and ML assistance are part of the complete target product but lower in implementation order

### 8.2 Asoview! Gift

#### Product Goal

Allow buyers to purchase experience gifts and allow recipients to redeem them into a downstream booking, ticket, or kit-like outcome.

#### Required Experience

- `[Observed]` Separate storefront behavior
- `[Observed]` Gift purchase flow
- `[Observed]` Shareable digital gift flow
- `[Observed]` Recipient login or registration behavior
- `[Observed]` Multiple redemption outcomes:
  - activity reservation
  - ticket exchange
  - kit or shipment path
- `[Observed]` One-time code semantics
- `[Observed]` Expiration-sensitive behavior
- `[Inferred]` Gift redemption must transform into another trackable right or order outcome

#### Complete Product Scope

- Buyer browsing
- Buyer checkout
- Share flow
- Recipient receive flow
- Recipient redeem flow
- Post-redemption status handling
- Delivery/kit branch where applicable

#### Architecture Fit

- `[Inferred]` Gift should reuse the same shared commerce and entitlement primitives rather than being modeled as an isolated e-commerce subsystem

### 8.3 Asoview! Furusato Nozei

#### Product Goal

Allow users to donate to municipalities and receive immediately usable experience-linked benefits, while also supporting municipality-side operations.

#### Required Experience

- `[Observed]` Municipality and return discovery
- `[Observed]` Donation/payment flow
- `[Observed]` Immediate issuance behavior
- `[Observed]` Coupon-like and e-ticket-like benefit types
- `[Observed]` Region and stacking restrictions
- `[Observed]` Staff-mediated ticket usage behavior where applicable
- `[Inferred]` Municipality-side admin and operations are part of the product scope, not only the consumer flow

#### Complete Product Scope

- Municipality discovery
- Donation and issuance
- Coupon application and constraints
- Ticket display and usage
- Municipality/admin operational workflows
- Return/expiry/reconciliation-related behavior

#### Architecture Fit

- `[Inferred]` Furusato should be treated as a domain extension of the shared commerce platform with municipality-specific operational modules

### 8.4 Asoview! Overseas

#### Product Goal

Allow Japan-first users to discover and buy overseas experiences while still handling multilingual content, deadlines, cancellation rules, and voucher delivery.

#### Required Experience

- `[Observed]` Country and city discovery
- `[Observed]` Product detail pages with overseas-specific attributes
- `[Observed]` Voucher/e-voucher delivery behavior
- `[Observed]` FX-aware price display/handling
- `[Inferred]` Japanese market first, but multilingual and overseas-specific attributes are first-class from the beginning

#### Complete Product Scope

- Country/city browsing
- Detail and plan selection
- Consent and restriction acknowledgement
- Purchase and voucher delivery
- Supplier-related and multilingual content handling

#### Architecture Fit

- `[Inferred]` Overseas should remain inside the same core commerce architecture while adding location, supplier, multilingual, and FX-oriented extensions

### 8.5 UraKata Ticket

#### Product Goal

Provide facility operators with ticket product management, inventory control, direct sales support, reporting, and dedicated mobile-based check-in.

#### Required Experience

- `[Observed]` Product and ticket type registration
- `[Observed]` Open inventory and datetime inventory modes
- `[Observed]` Direct sale / ticket distribution behavior
- `[Observed]` Check-in and validation flows
- `[Observed]` Reporting on sales and check-ins
- `[Observed]` Staff-operated validation is core
- `[Inferred]` Dedicated mobile scanner app is required from the beginning
- `[Inferred]` Device authentication and operator-side check-in controls are required

#### Complete Product Scope

- Facility setup
- Product/ticket type setup
- Inventory management
- Direct-sale flow
- Check-in operations
- Reporting
- Staff/device management

#### Architecture Fit

- `[Observed]` Public engineering materials describe this area with separate surfaces such as partner dashboard, backend API, direct sales, and Fast-In
- `[Inferred]` The clone should preserve this decomposed service/surface mentality rather than collapsing the product into one generic admin tool
- `[Inferred]` The clone should allow heterogeneous operator-facing UI implementation if that better reflects the public Asoview pattern

### 8.6 UraKata Reservation

#### Product Goal

Provide reservation-management SaaS for activity/class operators and the related consumer-facing reservation flow exposed from operator sites.

#### Required Experience

- `[Observed]` Calendar-based availability management
- `[Observed]` Reservation request flow
- `[Observed]` Email verification / one-time-link flow
- `[Observed]` Pending, waitlist, and approval-style states
- `[Observed]` Operator-side reservation board and customer management
- `[Inferred]` This product must include both:
  - operator-side reservation SaaS
  - consumer-facing reservation flow connected to the operator's offering

#### Complete Product Scope

- Reservation setup and inventory
- Consumer reservation request flow
- Email confirmation
- Manual approval/waitlist operations
- Reservation board and CRM-like management

#### Architecture Fit

- `[Inferred]` Reservation should be modeled as a distinct product app over shared booking/inventory primitives rather than being absorbed into UraKata Ticket

### 8.7 AREA GATE

#### Product Goal

Provide a tourism/DMO product that allows official or partner destination sites to expose bookable content with analytics and multilingual support.

#### Required Experience

- `[Observed]` Separate tourism/DMO product positioning
- `[Observed]` Official-site entry and distribution behavior
- `[Observed]` Multilingual support expectations
- `[Observed]` Data collection / KPI expectations
- `[Inferred]` AREA GATE should remain a separate product app even if it shares modules with the broader platform

#### Complete Product Scope

- Tourism content/product management
- Official-site booking entry
- Shared commerce flow integration
- Partner analytics
- Multilingual support

#### Architecture Fit

- `[Observed]` Public positioning suggests a separate tourism/DMO solution rather than a marketplace skin
- `[Inferred]` The clone should keep AREA GATE as a separate app and business surface

### 8.8 Asoview Ads

#### Product Goal

Provide a full advertiser workflow from account and campaign setup through delivery and reporting.

#### Required Experience

- `[Observed]` Self-serve advertiser-like management
- `[Observed]` CPC model
- `[Observed]` Reporting expectations
- `[Inferred]` The clone should model the full advertiser workflow, not just placement surfaces

#### Complete Product Scope

- Advertiser account setup
- Campaign creation
- Creative management
- Serving behavior
- Impression/click tracking
- CPC reporting

#### Architecture Fit

- `[Inferred]` Ads should integrate with shared analytics and account primitives while remaining an independent product/app surface

## 9. Shared Behavioral Rules

### 9.1 Inventory and Availability

- `[Inferred]` The platform must support both open inventory and datetime-specific inventory
- `[Inferred]` Booking and purchase concurrency must be protected
- `[Assumption]` Temporary inventory hold during checkout is required

### 9.2 Ticket Usage

- `[Observed]` Staff-side control is core to valid ticket consumption
- `[Observed]` Some tickets should not be displayable until a valid time window
- `[Inferred]` User-triggered irreversible self-consumption must be guarded against by UI and permission design

### 9.3 Gift Rights

- `[Observed]` Gift codes are one-time use
- `[Observed]` Expiration and redemption mode matter
- `[Inferred]` Redemption must create an auditable transformation into another downstream right or order state

### 9.4 Furusato Restrictions

- `[Observed]` Coupon restrictions, regional restrictions, and application constraints matter
- `[Observed]` One-payment-one-coupon semantics are part of the product behavior
- `[Inferred]` Return, cancellation, and expiry outcomes must be explicit in both UX and domain modeling

### 9.5 Reservation Workflows

- `[Observed]` Request/waitlist/approval semantics are part of the reservation experience
- `[Inferred]` Reservation flows require richer state modeling than simple confirmed/cancelled

## 10. Search and Discovery

### 10.1 Initial Implementation Priority

- keyword
- area
- category
- date

### 10.2 Full Product Target

- `[Inferred]` The complete product family should eventually include richer discovery quality than simple filtering alone
- `[Assumption]` Advanced search quality, recommendation, and AI-assisted improvements remain lower implementation priority, not out of scope

### 10.3 Architecture Signal

- `[Observed]` Search is publicly treated as a specialized subsystem, not just a simple SQL filter layer
- `[Assumption]` The clone should preserve that mindset even if exact search technology is finalized in `technical_design.md`

## 11. Shared Domain Scope

Planning must account for these shared domain objects:

- users
- operators
- tenants
- venues
- products
- product variants
- inventory slots
- orders
- order items
- payments
- entitlements
- ticket passes
- gift codes
- donations
- coupons
- check-in events
- staff devices
- ad accounts
- ad campaigns
- ad creatives
- reviews
- favorites
- points

### 11.1 Modeling Rules

- `[Inferred]` Shared commerce objects should remain generic
- `[Inferred]` Product-specific behavior should extend the core model rather than fork it unnecessarily
- `[Assumption]` Consumer identity and operator tenancy should be distinct concerns even when a real person belongs to both

## 12. API and Data Requirements

This PRD does not finalize exact endpoint lists or schemas, but it requires implementation-detail planning for:

- catalog
- availability
- cart and checkout
- payments
- mypage
- tickets
- gift redemption
- donation and coupon application
- operator inventory
- operator reservation management
- scanner validation
- ads workflow
- analytics ingestion

### 12.1 Required State Machines

`technical_design.md` must define state transitions for:

- order
- payment
- reservation
- entitlement
- ticket
- gift code
- coupon
- check-in
- ad campaign

### 12.2 Architecture Signal

- `[Observed]` Public materials indicate contract-first/API-schema-oriented practices
- `[Assumption]` The clone should preserve a schema-first contract mindset, likely with protocol-based interface definitions, even where external delivery remains REST-like

## 13. GCP Product Constraints

### 13.1 Platform Direction

- `[Assumption]` GCP is mandatory
- `[Assumption]` A single GCP project is the confirmed direction
- `[Assumption]` GCP-native or GCP-aligned services should be preferred where there is a credible alternative to non-GCP tooling
- `[Assumption]` GCP substitutions should preserve the publicly visible Asoview architectural shape, especially:
  - Java/Spring Boot-oriented backend services
  - modular-monolith-plus-microservices decomposition
  - unified gateway/API edge
  - Kubernetes-based runtime expectations
  - specialized search and analytics subsystems
  - contract-first API design
  - heterogeneous UI reality where publicly signaled

### 13.2 Production-Grade Requirements

- RBAC
- multi-tenant isolation where applicable
- audit logs
- idempotent mutation handling
- retry-safe webhook processing
- monitoring and alerting
- structured event logging
- secrets management
- analytics support
- operational reporting

### 13.3 AI Feature Positioning

- `[Assumption]` Vertex AI features remain in scope
- `[Assumption]` Vertex AI features should be implemented after more basic core product flows, not before them

### 13.4 Persistence Direction

- `[Observed]` Public evidence supports a mixed relational and newer-Spanner-capable persistence reality
- `[Assumption]` The clone should treat `Cloud Spanner` as a strong candidate for domains where it best matches the observed Asoview direction, rather than treating it as an exotic option

## 14. Japan-First Strategy

- `[Assumption]` Japan-first defaults should drive primary UX, pricing assumptions, support flows, and content expectations
- `[Inferred]` Overseas and AREA GATE still require multilingual capability from the start
- `[Assumption]` Localization should be modular rather than globally maximized across every product surface from day one

## 15. Analytics and Reporting

The complete product family must support:

- consumer funnel analytics
- sales analytics
- reservation analytics
- check-in analytics
- gift redemption analytics
- furusato issuance and redemption analytics
- overseas booking analytics
- AREA GATE partner analytics
- ads impression/click/CPC analytics

### 14.1 Requirement

- `[Inferred]` Shared event and reporting semantics should exist across the product family even if implementation detail differs by product

### 15.2 Architecture Signal

- `[Observed]` Data and analytics are not peripheral in public Asoview materials; they are part of the operating model
- `[Assumption]` The clone should therefore model analytics/reporting as core platform capabilities, not optional afterthoughts

## 16. What This Document Confirms

- All eight products are part of the clone target
- The target is a high-fidelity internal study clone
- Exact public behavior should be reproduced where observable
- Missing public details should be inferred in a product-faithful way
- Products are separate apps that share platform modules
- Shared identity exists, but surfaces stay product-specific
- Single-project GCP is confirmed
- Asoview-style architecture tendencies should be preserved where publicly signaled
- Java/Spring Boot, microservices, gateway-oriented APIs, Kubernetes shape, and contract-first thinking are part of the target architectural bias
- Fast-In-style dedicated mobile scanning remains in scope
- Vertex AI features are in scope but lower implementation priority
- Social and loyalty features are in scope even if later in delivery order
- AREA GATE is a separate product app
- Ads should cover full advertiser workflow

## 17. What This Document Deliberately Does Not Lock

- exact frontend framework
- exact backend framework
- exact ORM
- exact search product
- exact database product details beyond GCP direction
- exact route-by-route maps
- exact endpoint lists
- exact table schemas

Those belong in the implementation-detail document.
That technical design document is named `technical_design.md`.
