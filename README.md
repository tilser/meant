# Meant

**Everything meant for you.**

Meant is a **Personal Commerce OS**: one intelligent, user-first system for discovering, comparing, buying, and managing products across independent merchants.

Meant is not a marketplace and it is not a frontend for any single commerce platform. It connects to merchants through the Universal Commerce Protocol (UCP), provider catalogs, and provider-specific extensions, then turns those fragmented systems into one consistent shopping experience.

## Vision

Online shopping is organized around stores. People have to repeat the same search, filters, preferences, address details, and checkout decisions on every site.

Meant reorganizes commerce around the person.

Meant understands what a user wants, remembers their durable preferences, searches every compatible merchant, identifies when multiple merchants sell the same product, ranks both products and offers, and completes the purchase through the best capability each merchant supports.

The merchant remains the seller of record. Meant provides the intelligence and orchestration that connects the user to that merchant.

## The Meant Experience

1. The user describes an intent, such as: "Find me a durable organic cotton hoodie under EUR 100."
2. Meant searches multiple sources in parallel, including provider-wide catalogs and individual UCP merchants.
3. Results from the same merchant are deduplicated. The same product sold by different merchants is grouped into one canonical product with multiple offers.
4. Products are ranked for personal relevance. Offers are ranked independently using price, availability, delivery, merchant quality, and checkout reliability.
5. The product detail shows why the product fits, which merchants sell it, and which offer Meant recommends.
6. The user builds one coordinated cart experience. Merchant boundaries remain explicit because each merchant owns its own inventory, totals, and transaction.
7. Checkout stays inside Meant whenever the merchant supports embedded checkout. Meant progressively uses native or delegated capabilities when they are available and safe.
8. Meant monitors orders, delivery, returns, and recurring shopping needs after purchase.

A multi-merchant cart is a unified user experience, not a single cross-merchant transaction. Meant coordinates a separate checkout for each merchant and makes the sequence feel continuous.

## Personalization

Users can define persistent preferences and goals, for example:

- materials and ingredients to include or avoid
- health, sustainability, and ethical requirements
- preferred brands, sizes, fit, and categories
- budget and delivery constraints
- quality and review expectations
- products they already own and items they need to replenish

Meant applies these preferences to every search and explains why each recommendation is meant for that user.

## Platform Architecture

Meant consists of three layers:

### Commerce Intelligence

- intent understanding and query planning
- cross-source product identity and offer grouping
- hard filtering and personalized product ranking
- offer ranking across merchants
- recommendation explanations, review insights, and user memory

### Commerce Runtime

- UCP profile and capability negotiation
- provider catalogs and provider-specific extensions
- merchant and integration registry
- authentication, scopes, token lifecycle, and rate-limit handling
- cart, checkout, embedded checkout, and payment orchestration
- retries, fallbacks, conformance testing, and observability
- order lifecycle and webhooks

### Commerce Surfaces

- the Meant web and mobile shopping experience
- APIs and SDKs for third-party agents
- partner and white-label experiences

Every Meant and partner surface runs on the same headless, provider-neutral Commerce Runtime. Third-party agents use the same merchant infrastructure without rebuilding every catalog, authentication, cart, checkout, and order integration.

## Commerce Compatibility

UCP defines the common commerce contract. Providers can add capabilities, extensions, authentication requirements, and operational constraints. Meant supports the shared protocol while preserving those richer provider-specific features instead of reducing every merchant to the lowest common denominator.

Merchant support is capability-based rather than binary. A merchant integration can independently support:

- catalog search and product lookup
- cart creation and updates
- checkout session management
- embedded checkout
- address, fulfillment, or payment delegation
- direct checkout completion
- order reads and lifecycle webhooks

Meant provides gold-standard compatibility with Shopify through Global Catalog discovery, Shopify UCP catalog extensions, authenticated cart and checkout tools, Checkout Kit with the Embedded Checkout Protocol (ECP), direct checkout completion when authorized, and order lifecycle tools. Other UCP providers and commerce platforms integrate through the same architecture.

Production integrations use Meant's HTTP clients and runtime services directly. Shopify's CLI and tutorial tooling are useful for exploration and conformance checks, but they are not runtime dependencies.

## Product Principles

- **User-first:** Optimize for the user's intent and long-term interests, not for one merchant or provider.
- **Provider-neutral:** Provider-specific code ends at an adapter boundary and does not define the core product model.
- **Capability-aware:** Negotiate what each merchant can do and progressively enhance the experience.
- **Products plus offers:** Group equivalent products while preserving every merchant offer as an independent purchase option.
- **Trustworthy ranking:** Make recommendations explainable and prevent one source, affiliate relationship, or large catalog from dominating unfairly.
- **Money safety:** Reconcile totals, preserve idempotency, protect credentials, and fail safely during checkout.
- **Current data:** Refresh price, availability, and fulfillment before purchase and respect every provider's data-use rules.
- **Observable execution:** Measure discovery quality, provider reliability, checkout completion, latency, and fallbacks.

## Business Model

The consumer product is free for users. Revenue comes from merchant or affiliate commissions on completed purchases and from infrastructure used by third-party commerce agents and surfaces.

The Meant experience and external clients share the same Commerce Runtime, making provider coverage, reliability, and checkout infrastructure reusable across the entire ecosystem.

## Repository

This repository is a monorepo with a Java Spring Boot backend and a TanStack Start frontend.

## Stack

- Backend: Java 25, Spring Boot 4.1.0, Maven, Hibernate via Spring Data JPA, Liquibase
- Database: PostgreSQL
- Frontend: TanStack Start, React, TypeScript, Tailwind CSS, Bun

## Layout

```text
backend/   Spring Boot API
frontend/  TanStack Start app
plans/     Product and engineering implementation plans
```

## Local Development

Start PostgreSQL:

```sh
docker compose up -d postgres
```

Optional environment files:

```sh
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
```

Run the backend:

```sh
cd backend
mvn spring-boot:run
```

Run the frontend:

```sh
cd frontend
bun install
bun run dev
```

The backend listens on `http://localhost:8080`.
The frontend listens on `http://localhost:3000`.

## Useful Commands

```sh
cd backend && mvn test
cd frontend && bun run build
```

## Continuous Integration

GitHub Actions runs CI on every pull request and on pushes to `main`.

Reproduce the backend job locally:

```sh
cd backend
mvn verify
```

`mvn verify` runs unit tests and the Testcontainers-backed integration tests named `*IT`.

Reproduce the frontend job locally:

```sh
cd frontend
bun install --frozen-lockfile
bun test
npm run typecheck
npm run build
```
