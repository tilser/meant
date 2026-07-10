# Personal Commerce OS — Implementation Plan

Status: Proposed  
Last updated: 2026-07-10  
Primary milestone: Shopify discovery to embedded checkout  

## Purpose

This document turns the Meant product vision into an ordered engineering backlog. It is written to be split into implementation tickets and delivered incrementally without turning Shopify into a special case throughout the application.

For the scope covered here, this document is the product-aligned source of truth. Existing UCP, checkout, and refactor plans remain useful technical background. If an older plan conflicts with the provider model, grouped product/offer model, Shopify Global Catalog strategy, or embedded checkout strategy below, this plan takes precedence.

## Target Outcome

A user can:

1. Search once across Shopify Global Catalog and Meant's existing UCP merchant network.
2. See duplicate results from the same merchant removed.
3. See the same product sold by different merchants as one product with multiple offers.
4. Understand why the product is recommended and why one offer is considered the best.
5. Add a selected offer to the correct merchant-scoped cart.
6. Complete Shopify checkout inside Meant through Checkout Kit and ECP.
7. Use direct `complete_checkout` when Shopify has authorized Meant and the checkout state requires it.
8. Fall back safely to the merchant's `continue_url` when embedded checkout is unavailable.

The implementation must also establish reusable provider boundaries so the next catalog or marketplace can be integrated without adding provider checks throughout discovery, cart, and checkout code.

## Decisions Already Made

- Shopify is the first gold-standard provider, not the definition of Meant's core domain.
- Shopify Global Catalog is queried once per broad search in parallel with the existing Meant merchant discovery path.
- Meant does not fan out to every individual Shopify storefront after a Global Catalog search. Storefront search is reserved for explicit merchant-scoped search, coverage fallback, or enrichment of selected candidates.
- Deduplication first removes duplicate products/offers from the same merchant. Cross-merchant identity resolution groups equivalent products but preserves every merchant offer.
- The public product model is `Product + Offers + Provenance`, not a flat list of merchant products.
- Retrieval scores from different providers are not directly comparable. Meant owns score calibration, product ranking, offer ranking, and source diversity.
- Shopify Token tier is a production requirement. It is the path to the highest limits, authorized `complete_checkout`, and order scopes.
- Capability advertisement, authorization, rollout enablement, and runtime health are separate facts. A merchant advertising checkout does not by itself authorize Meant to complete a purchase.
- Checkout Kit with ECP is the preferred Shopify checkout experience. Direct checkout completion is capability-gated. External handoff remains the fallback.
- A multi-merchant cart is one coordinated experience but contains separate merchant transactions and checkout sessions.
- Orders are not part of the first delivery milestone, but checkout identifiers and provenance must be persisted now so order support can be added without a data migration or broken history.
- Shopify CLI and AI Toolkit are not production dependencies. They may be used manually for exploration or conformance checks only.
- The Meant frontend is the first client of a headless Commerce Runtime. Public third-party APIs come after the internal contracts have been proven in production.

## Target Runtime Flow

```text
User intent
  ├─ Shopify Global Catalog provider
  ├─ Existing Meant UCP merchant provider
  └─ Future catalog providers
          ↓
Provider-neutral candidates with provenance
          ↓
Same-merchant deduplication
          ↓
Cross-merchant product identity resolution
          ↓
Product ranking + offer ranking + diversity policy
          ↓
Product detail with multiple offers
          ↓
Selected offer → merchant cart → merchant checkout
          ↓
Checkout Kit / ECP → authorized complete_checkout → persisted outcome
          ↓
External continue_url fallback when required
```

## Delivery Rules

- Each ticket should be independently reviewable and leave the main branch deployable.
- Provider-specific identifiers may live in integration records and provenance, but not as mandatory fields in the canonical product domain.
- All new controller and service contracts must use concrete typed records. Do not introduce raw maps or `Object` fields into request flow.
- Database changes use Liquibase XML under `backend/src/main/resources/db/changelog/migration`.
- Remote calls stay outside database transactions.
- Money uses integer minor units or protocol decimal strings with an explicit currency. Never use floating point for totals or payment decisions.
- Do not log access tokens, ECP auth values, checkout payload credentials, payment credentials, buyer addresses, signatures, or complete raw checkout responses.
- Every remote provider call has explicit connect/read/overall deadlines, concurrency limits, retry classification, and metrics.
- A provider failure produces partial results or a scoped checkout fallback; it must not fail unrelated providers or merchant carts.

## Phase 0 — Access and Domain Foundations

### PCOS-001 — Obtain Shopify production-grade agent access

Priority: P0  
Dependencies: None  
Owner: Product/platform with backend support  

**Goal**

Create the external Shopify prerequisites early because authorization lead time must not block the completed implementation.

**Work**

- Create the Shopify Dev Dashboard account and catalog API credentials.
- Register the production and non-production Meant agent identities/profile URLs.
- Confirm the process and eligibility for Checkout Kit/ECP access.
- Request the permission needed for `complete_checkout`.
- Request `read_global_api_orders` for the later orders phase.
- Record granted scopes, traffic limits, token expiry behavior, approved redirect/origin values, and environment separation in an internal access matrix.
- Store secrets in the deployment secret manager. Add only variable names and safe examples to repository configuration.
- Do not add Shopify CLI or AI Toolkit as an application dependency.

**Acceptance criteria**

- A non-production client ID and secret can obtain a bearer token from `https://api.shopify.com/auth/access_token` using the client credentials flow.
- The decoded token metadata shows the expected catalog scope and known limits.
- The status of Checkout Kit, `complete_checkout`, and order scope approvals is documented separately from code readiness.
- No secret or bearer token is committed, logged, exposed in frontend configuration, or returned by an API.

**References**

- [Authenticate your agent](https://shopify.dev/docs/agents/get-started/authentication)
- [Auth and rate limiting](https://shopify.dev/docs/agents/profiles/auth-and-rate-limiting)

### PCOS-002 — Introduce a provider and merchant integration registry

Priority: P0  
Dependencies: None  
Implementation status: Complete

**Goal**

Represent a merchant's zero-to-many commerce integrations instead of assuming one domain, one UCP endpoint, and one execution path.

**Work**

- Add a `MerchantIntegration` entity in the merchant module and a Liquibase XML migration.
- Model typed values for provider, integration type, auth strategy, status, external merchant ID, shop/domain identity, endpoint, protocol version, and captured metadata timestamps.
- Support at least `GENERIC_UCP` and `SHOPIFY`, with integration types for global catalog provenance, storefront catalog, cart, checkout, and future orders.
- Backfill current merchants as generic UCP integrations without deleting existing merchant data in the first migration.
- Add repository and service queries for resolving an integration by merchant, provider identity, external merchant ID, or verified domain.
- Keep provider-specific raw metadata isolated as an archive at the integration boundary; promote fields to columns only when they are filtered or joined.

**Acceptance criteria**

- One merchant can hold more than one integration and auth strategy.
- Two integrations cannot claim the same provider/external identity within the same environment.
- Existing merchant discovery and carts continue to resolve after migration.
- No `if (domain contains shopify)` or storefront-theme detection is introduced.

**Tests**

- Repository uniqueness and lookup integration tests.
- Migration/backfill integration test.
- Resolution tests for one merchant with Shopify and generic UCP integrations.

### PCOS-003 — Replace the checkout boolean with a capability and execution policy

Priority: P0  
Dependencies: PCOS-002  
Implementation status: Complete

**Goal**

Remove the current conflation between an advertised checkout capability and authorization to execute a sensitive operation.

**Work**

- Replace `nativeCheckoutEnabled` as the routing decision in `MerchantCartProviderLookupService` and checkout response DTOs.
- Define a typed effective capability model that separately records:
  - capability advertised by the merchant/provider,
  - Meant authentication tier and granted scopes,
  - product rollout enablement,
  - current integration health,
  - supported fallback.
- At minimum expose independent decisions for catalog, cart, checkout session, embedded checkout, direct completion, order reads, and order webhooks.
- Keep a temporary compatibility mapping for existing frontend clients, then remove the boolean after all call sites migrate.
- Make the selected execution rail and the reason for ineligibility observable.

**Acceptance criteria**

- An advertised checkout capability cannot enable `complete_checkout` without the required token permission and rollout flag.
- Embedded checkout can be enabled while direct completion is disabled.
- A provider outage can disable one execution rail without disabling the merchant or unrelated capabilities.
- The frontend receives an explicit next action rather than inferring behavior from a boolean.

### PCOS-004 — Define the canonical Product, Offer, and Provenance contracts

Priority: P0  
Dependencies: PCOS-002  
Implementation status: Complete

**Goal**

Create the provider-neutral result model required for grouped products and selectable merchant offers.

**Work**

- Introduce typed service DTOs for `ProductCandidate`, `CanonicalProduct`, `Offer`, `OfferIdentity`, `ProductIdentityEvidence`, and `ResultProvenance`.
- Give each offer a stable key derived from provider integration, external merchant identity, product ID, variant ID, and selling-plan context where applicable.
- Keep merchant-specific price, availability, delivery, checkout URL, and selected variant on the offer.
- Keep shared title, description, media, attributes, materials, certifications, and product identifiers on the canonical product, with source attribution.
- Support multiple provenance records when the same offer is observed through multiple discovery paths.
- Add a versioned controller response that returns grouped products while the existing flat response remains available during frontend migration.

**Acceptance criteria**

- The same product from three merchants is represented as one product with three offers.
- Duplicate observations of the same merchant variant become one offer with multiple provenance entries.
- Adding a future provider does not require a new mandatory field on `CanonicalProduct`.
- Shopify GIDs and UPIDs do not become universal identifiers in core DTOs; they are represented as typed external identity evidence/provenance.

### PCOS-004A — Decouple offer identity, discovery provenance, and local merchant routing

Priority: P0
Dependencies: PCOS-004

**Goal**

Allow provider-wide discovery to represent offers from unknown external sellers without creating local merchant rows, while deduplicating the same commercial offer across discovery paths.

**Work**

- Separate commercial seller/product/variant/configuration identity from typed discovery-source identity and an optional local `MerchantIntegration` routing link.
- Prefer provider-namespaced external merchant identity, with local integration identity only as an explicit fallback when a provider supplies no stable merchant identity.
- Version the canonical offer key and include variant options, bundle components, and selling-plan context using deterministic collision-safe encoding.
- Preserve the flat search API and legacy `productKey`/`productHash`; expose the richer model through the grouped V1 response and generated frontend OpenAPI types.

**Acceptance criteria**

- A Shopify Global Catalog offer is valid without a local merchant or integration, and the same external offer observed through Global and Storefront Catalog becomes one offer with both provenance records.
- Resolving local routing later does not change an externally scoped offer key; generic UCP merchants without external seller identity remain collision-safe through the local fallback.
- Sellers, variants, selected options, bundle components, and selling plans remain distinct, while semantically unordered identity inputs are canonicalized.
- The grouped V1 OpenAPI route and nested source/routing contracts are checked into the frontend schema without replacing either flat search route.

## Phase 1 — Shopify Authentication and Global Discovery

### PCOS-005 — Implement Shopify bearer token management

Priority: P0  
Dependencies: PCOS-001  
Implementation status: Complete

**Goal**

Authenticate Shopify traffic at Token tier without leaking credentials or coupling authentication to every UCP merchant.

**Work**

- Add validated configuration for Shopify client ID, secret reference, token endpoint, refresh skew, and environment.
- Implement a typed Shopify token client under the UCP transport client layer.
- Exchange client credentials for a bearer token at runtime and parse expiry, scopes, and limits from the returned JWT without treating its payload as authorization proof beyond Shopify's documented contract.
- Cache a token only until a safe interval before expiry; deduplicate concurrent refreshes.
- Refresh once and retry when Shopify rejects an expired token. Do not blindly retry other authentication failures.
- Introduce an outbound auth strategy that attaches the bearer token only to verified Shopify endpoints/integrations.
- Redact `Authorization`, client secret, `ec_auth`, and token-shaped values from logging and error reporting.

**Acceptance criteria**

- Concurrent Shopify calls share one valid token refresh.
- Expired tokens refresh automatically without exposing the token to callers.
- Generic UCP merchants do not receive Shopify credentials.
- Missing scopes produce a typed capability-unavailable result and metric, not an ambiguous 500 response.

**Tests**

- Token acquisition, cache hit, refresh skew, concurrent refresh, 401 refresh, missing scope, and redaction tests using a fake HTTP server.

### PCOS-006 — Add the Shopify Global Catalog provider

Priority: P0  
Dependencies: PCOS-004A, PCOS-005

**Goal**

Search Shopify's cross-merchant catalog as a first-class discovery source.

**Work**

- Add a Shopify Global Catalog client/adapter using `https://catalog.shopify.com/api/ucp/mcp` and the existing typed UCP transport where possible.
- Send the Meant agent profile and Shopify bearer token.
- Implement typed `search_catalog`, `lookup_catalog`, and `get_product` handling, including Shopify's catalog extension and version metadata.
- Normalize UPID product groups, sellers, variants, price, availability, shop identity, and checkout links into the contracts from PCOS-004.
- Keep Shopify response DTOs inside `plugin.catalog.shopify`; do not leak them into user or merchant controller contracts.
- Apply explicit per-request timeouts, result limits, error classification, and circuit breaking.
- Use Storefront Catalog only when the request is explicitly merchant-scoped, Global Catalog coverage requires a targeted fallback, or selected results need merchant-specific enrichment.

**Acceptance criteria**

- One search request calls Global Catalog once, regardless of the number of Shopify merchants in Meant's database.
- A Shopify UPID group becomes one product candidate with independent seller offers.
- A failure returns a source-scoped error and allows other discovery providers to complete.
- Provider endpoint and protocol version are configuration/negotiation data rather than hard-coded throughout orchestration code.

**References**

- [About catalogs](https://shopify.dev/docs/agents/catalog)
- [Shopify Global Catalog](https://shopify.dev/docs/agents/catalog/global-catalog)

### PCOS-007 — Build federated discovery orchestration

Priority: P0  
Dependencies: PCOS-004, PCOS-006  

**Goal**

Run Shopify Global Catalog and current Meant merchant discovery in parallel under one bounded search budget.

**Work**

- Extract a provider-neutral discovery source contract from the current `MerchantSemanticProductSearchService` flow.
- Implement adapters for Shopify Global Catalog and the existing merchant-semantic UCP fan-out.
- Query providers concurrently with per-source concurrency, deadline, and candidate budgets.
- Return partial results when one source fails or times out.
- Preserve the existing streamed candidate path and attach source/provenance to every event.
- Prevent double-searching a Shopify merchant through the generic path when the same query is already covered by Global Catalog, except for explicit fallback/enrichment policy.
- Define deterministic completion and cancellation when the frontend disconnects.

**Acceptance criteria**

- Shopify and generic UCP discovery start concurrently.
- A failing or slow provider does not block successful source results beyond the overall search deadline.
- The same Shopify merchant is not redundantly fanned out by default.
- Search cancellation stops outstanding provider work.

### PCOS-008 — Implement same-merchant deduplication and cross-merchant product grouping

Priority: P0  
Dependencies: PCOS-007  

**Goal**

Produce one product cluster with independent offers while avoiding false merges that could send a user to the wrong variant.

**Work**

- Deduplicate exact offers first using provider integration, merchant identity, product ID, variant ID, selected options, and selling-plan identity.
- Treat Shopify UPID grouping as authoritative within the Shopify source.
- Resolve cross-source product identity using descending-confidence evidence:
  1. GTIN/UPC/EAN or another universal product ID,
  2. normalized brand plus MPN/model,
  3. verified provider mapping,
  4. normalized canonical URL for the same merchant,
  5. high-confidence title/attribute/variant/media similarity.
- Never merge on semantic similarity alone below an explicit confidence threshold.
- Preserve identity evidence and confidence for debugging and evaluation.
- Ensure a selected offer always retains its original merchant and variant identity even when product-level display data comes from another source.

**Acceptance criteria**

- Duplicate observations from one merchant collapse to one offer.
- Equivalent products from different merchants group while retaining all offers.
- Different sizes, colors, bundles, generations, and selling plans are not incorrectly merged.
- Low-confidence matches remain separate and are measurable for later evaluation.

**Tests**

- A golden fixture suite covering exact duplicate, UPID group, GTIN match, brand/MPN match, variant mismatch, bundle mismatch, and ambiguous semantic similarity.

### PCOS-009 — Separate product ranking from offer ranking

Priority: P0  
Dependencies: PCOS-008  

**Goal**

Rank the best products for the user and then rank purchase options for each product without letting catalog size or affiliate economics dominate relevance.

**Work**

- Calibrate source retrieval scores into provider-neutral features; never directly compare raw Shopify and Voyage scores.
- Apply hard constraints and availability checks before model reranking.
- Rank canonical products using intent fit, durable preferences, taste, inventory relationship, quality evidence, and confidence.
- Rank offers independently using landed price when known, availability, delivery, merchant trust, return policy, checkout capability, historical reliability, and freshness.
- Add source and merchant diversity rules to the top result window.
- Make commercial/commission signals ineligible as hidden relevance boosts. If used, restrict them to an explicitly disclosed tie-break policy.
- Store a typed explanation for both product recommendation and best-offer recommendation.

**Acceptance criteria**

- A large provider cannot fill the entire result window solely due to candidate volume.
- Offer ordering changes without changing product relevance when only merchant price or reliability changes.
- Ranking decisions can be reproduced from logged feature names and version identifiers without logging personal raw prompts or credentials.

### PCOS-010 — Enforce provider-specific data retention and freshness policies

Priority: P0  
Dependencies: PCOS-006, PCOS-007  

**Goal**

Prevent the existing persistent search cache from violating Shopify catalog data-use rules or serving stale price and availability.

**Work**

- Define a typed retention policy per discovery source and payload class.
- Default Shopify Global Catalog search payloads and images to session-only use until legal/product review confirms what may be persisted.
- Render merchant images from their source URL only in the permitted listing context; do not download them into Meant storage.
- Separate durable user interaction data from provider result payloads.
- Persist only provider identifiers and transaction/provenance fields required for saved products, cart, checkout, or audit when permitted.
- Rehydrate selected and saved products through lookup/get-product before displaying current commercial data.
- Refresh price, availability, selected variant, and fulfillment before cart/checkout decisions.
- Add source-aware invalidation to `UserProductSearchPersistenceService`; do not apply the current 24-hour behavior uniformly.

**Acceptance criteria**

- Shopify result payloads never enter an unapproved generic 24-hour cache path.
- Saved/cart products can be rehydrated without relying on a stale cached payload.
- Price or availability used for checkout comes from a current cart/checkout response.
- Retention decisions are covered by tests and documented next to the source adapter.

## Phase 2 — Grouped Product Experience

### PCOS-011 — Ship grouped search and product detail APIs

Priority: P0  
Dependencies: PCOS-008, PCOS-009, PCOS-010  

**Goal**

Expose canonical products, ranked offers, identity confidence, and recommendation explanations to the frontend.

**Work**

- Add versioned grouped search/product-detail controller responses with complete OpenAPI schemas.
- Make product detail lookup accept a Meant canonical product key and optional selected offer key, never an arbitrary provider URL.
- Return a recommended offer plus all eligible alternatives.
- Include price freshness, availability, merchant, delivery estimate when known, checkout experience level, and explanation fields per offer.
- Return source-scoped degradation flags when some offer enrichment fails.
- Generate frontend API types from the backend schema and migrate consumers off the flat result shape.

**Acceptance criteria**

- One product card opens a detail page that lists multiple merchant offers.
- The recommended offer is selected by default but the user can choose another merchant.
- Choosing an offer preserves its exact product/variant identity through cart creation.
- Partial enrichment failure does not remove valid offers or fail the product page.

### PCOS-012 — Build the grouped product and offer-selection frontend

Priority: P0  
Dependencies: PCOS-011  

**Goal**

Make cross-merchant grouping understandable and trustworthy instead of presenting it as an opaque deduplication step.

**Work**

- Render one search card per canonical product.
- On product detail, show merchant offers with price, availability, shipping when known, merchant identity, checkout mode, and best-offer explanation.
- Let the user explicitly select an offer before adding to cart.
- Preserve current personalization explanations at product level.
- Show stale/unavailable offers as refreshable or disabled, never silently substitute a merchant.
- Add analytics events for product view, offer view, offer selection, default-offer override, and add-to-cart.

**Acceptance criteria**

- The UI never implies that offers from different merchants are one transaction.
- Changing the selected offer changes the merchant/variant sent to cart.
- Keyboard, responsive, loading, empty, degraded, and error states are covered.
- Pure selection/grouping state has Bun tests; critical rendering gets the repository's chosen component/E2E coverage when available.

## Phase 3 — Shopify Cart and Embedded Checkout

### PCOS-013 — Bind selected offers to merchant-scoped carts

Priority: P0  
Dependencies: PCOS-003, PCOS-011  

**Goal**

Carry offer provenance into the correct cart integration and prevent provider or merchant substitution after selection.

**Work**

- Change add-to-cart input to use a server-resolved offer key rather than trusting client-supplied endpoint, merchant, price, or checkout URL.
- Resolve the offer to its `MerchantIntegration`, external variant, selected options, and execution policy on the backend.
- Persist provider, integration ID, external product/variant identity, and discovery provenance on cart lines or an associated immutable snapshot.
- Keep one remote cart per merchant/integration while presenting a unified local cart.
- Revalidate selected offer identity before creating or updating the remote cart.
- Preserve existing stale-cart recovery and make it provider-aware.

**Acceptance criteria**

- A Shopify offer routes to Shopify cart tools with Shopify auth.
- A generic UCP offer routes to its merchant integration without Shopify credentials.
- A client cannot change merchant, endpoint, or price by modifying the request.
- Duplicate add operations remain idempotent according to current cart semantics.

### PCOS-014 — Complete authenticated Shopify cart and checkout session support

Priority: P0  
Dependencies: PCOS-005, PCOS-013  

**Goal**

Run the full Shopify Cart MCP and Checkout MCP lifecycle through typed, token-authenticated provider routing.

**Work**

- Attach Shopify bearer auth through the integration auth strategy for `create_cart`, `get_cart`, `update_cart`, and `cancel_cart`.
- Convert a remote cart to checkout idempotently using `create_checkout(cart_id)`.
- Implement authenticated `get_checkout`, `update_checkout`, and `cancel_checkout` for Shopify.
- Respect PUT-style cart update semantics and always send the intended complete cart state.
- Persist remote cart/checkout IDs, status, `continue_url`, protocol version, integration ID, and last synchronization timestamp.
- Map `incomplete`, `requires_escalation`, `ready_for_complete`, processing, completed, cancelled, and failure states explicitly.
- Keep remote I/O outside database transactions.

**Acceptance criteria**

- A Shopify Global Catalog offer can be added, updated, removed, converted to checkout, refreshed, and cancelled.
- Repeating checkout creation for the same cart returns/reuses the same logical checkout.
- Unknown or new provider fields do not break typed core mapping; raw transport archives stay at the adapter boundary where permitted.
- No cart or checkout request/response containing buyer or credential data is logged in full.

**Reference**

- [Carts and checkout for agents](https://shopify.dev/docs/agents/carts-and-checkout)

### PCOS-015 — Add a secure embedded-checkout bootstrap API

Priority: P0  
Dependencies: PCOS-014  

**Goal**

Give the frontend the short-lived information required to open Checkout Kit without exposing the global Shopify client secret or bearer token.

**Work**

- Add a backend command that refreshes checkout state and decides the allowed next action from the effective capability policy.
- Return a short-lived embedded checkout session descriptor containing only the checkout URL, ECP version, allowed delegations, expiry, merchant display data, and an opaque Meant session ID.
- Generate or retrieve merchant-required `ec_auth` server-side and expose only the short-lived value required by ECP, never the global Shopify API token.
- Bind the session to the authenticated Meant user, cart, merchant integration, checkout ID, allowed origin, and expiration.
- Validate every completion/cancel callback against the session binding.
- Return an explicit external `continue_url` fallback when embedded checkout is unavailable.

**Acceptance criteria**

- The browser never receives Shopify client credentials or the reusable global API token.
- Another user cannot open or complete the embedded session.
- Expired/replayed bootstrap sessions fail safely and can be recreated.
- The API explicitly distinguishes embedded, direct-complete, and external-handoff next actions.

### PCOS-016 — Integrate Shopify Checkout Kit and the ECP lifecycle in the web app

Priority: P0  
Dependencies: PCOS-015  

**Goal**

Keep the buyer inside Meant while Shopify renders and operates the merchant checkout.

**Work**

- Add the official web Checkout Kit package behind a Meant-owned `EmbeddedCheckout` adapter/component.
- Do not let Shopify SDK types spread through cart pages or core frontend state.
- Open the server-issued checkout URL and handle `ready`, `start`, `complete`, cancel, failure, timeout, and recovery lifecycle callbacks.
- Refresh backend checkout state after completion or an ambiguous client event before declaring success.
- Add focus management, responsive layout, loading skeleton, close confirmation, browser compatibility handling, and accessible error recovery.
- Configure CSP, allowed origins, referrer policy, and logging redaction.
- Fall back to the external `continue_url` without losing cart state when Checkout Kit is unsupported or fails before purchase.
- Record checkout start, ready latency, completion, cancellation, SDK error, recovery, and fallback metrics.

**Acceptance criteria**

- A test Shopify checkout can be completed without navigating the Meant top-level page away from the application.
- `ec.complete` produces a verified completed state in Meant, not only a frontend success screen.
- Closing or reloading the surface can resume or reconcile the active checkout.
- Unsupported browsers receive a safe handoff.

**References**

- [Checkout Kit](https://shopify.dev/docs/agents/carts-and-checkout/checkout-kit)
- [Embedded Checkout Protocol](https://shopify.dev/docs/agents/carts-and-checkout/ecp)

### PCOS-017 — Coordinate multi-merchant checkout

Priority: P0  
Dependencies: PCOS-016  

**Goal**

Turn separate merchant checkouts into one understandable sequence without implying atomic payment or order semantics.

**Work**

- Create a local checkout journey containing one checkout step per merchant cart.
- Show merchant count, current step, completed merchants, remaining merchants, and failures.
- Launch one embedded checkout at a time and reconcile it before advancing.
- Allow retry, skip for later, or external fallback per merchant without losing completed steps.
- Prevent a failed second merchant from changing the recorded outcome of the first.
- Make prices and totals explicit per merchant; do not display an authoritative combined charge total unless clearly labeled as an estimate.

**Acceptance criteria**

- Two Shopify merchant carts create two independent checkout sessions in one Meant journey.
- Completing one merchant and cancelling another preserves accurate state for both.
- Refreshing the page resumes the next incomplete merchant step.
- Analytics and UI never report the multi-merchant journey as one order.

### PCOS-018 — Enable token-authorized `complete_checkout`

Priority: P0 for code readiness; rollout blocked by Shopify permission  
Dependencies: PCOS-003, PCOS-014, PCOS-016, PCOS-001 approval  

**Goal**

Use the existing checkout safety foundation to finalize eligible Shopify checkouts through the authorized token path.

**Work**

- Extend the checkout transport to support token authentication in addition to the existing signed request path.
- Gate calls on negotiated capability, granted token permission, checkout status, explicit buyer consent where required, and per-integration rollout enablement.
- Refresh checkout and reconcile merchant, line items, currency, minor-unit totals, fulfillment, and buyer-visible amount before completion.
- Reuse persisted idempotency keys and status-first reconciliation from the current native completion services.
- On timeout or unknown outcome, call `get_checkout` before any retry. Never blindly repeat a money-moving request.
- Interpret completed, processing, escalation/SCA, recoverable, cancelled, and terminal failure states.
- Record an immutable completion attempt/outcome without storing payment credentials.

**Acceptance criteria**

- The tool is never called for a token lacking the required permission.
- A retry after an unknown response cannot create a duplicate charge/order.
- A totals or currency mismatch blocks completion and returns the buyer to a safe refreshed state.
- When direct completion is unavailable, the same checkout remains usable through Checkout Kit or handoff.

### PCOS-019 — Implement ECP delegations progressively

Priority: P1 after base embedded checkout is stable  
Dependencies: PCOS-016, PCOS-018  

**Goal**

Make checkout progressively more convenient while advertising only delegations Meant can honor safely.

**Work**

- Build a typed delegation registry for fulfillment address changes, payment instrument selection, and payment credential requests.
- Start with buyer information prefill and lifecycle integration; do not request a delegation merely because Shopify advertises it.
- Add address delegation only after validation, privacy, persistence, and recovery behavior are defined.
- Add payment instrument/credential delegation only through approved tokenized wallet/payment handlers. Never collect or store raw primary account numbers.
- Negotiate delegations per checkout session and make unsupported requests fall back to merchant UI.
- Add timeout, cancellation, duplicate request, and out-of-order JSON-RPC handling.

**Acceptance criteria**

- Meant's requested `ec_delegate` list equals the handlers enabled and healthy for that session.
- Every JSON-RPC request receives one valid response or a defined cancellation/error response.
- Disabling a handler removes the advertised delegation without disabling embedded checkout.
- Security review approves payment delegation before production enablement.

## Phase 4 — Production Hardening and Release

### PCOS-020 — Add provider conformance, security, and observability coverage

Priority: P0 before production rollout  
Dependencies: PCOS-006 through PCOS-019  

**Goal**

Make provider compatibility and checkout safety measurable rather than relying on manual happy-path testing.

**Work**

- Build recorded, redacted contract fixtures for Shopify auth, catalog, cart, checkout, and ECP lifecycle messages.
- Add adapter conformance tests for required fields, unknown extensions, protocol versions, malformed responses, throttling, and permission failures.
- Add an end-to-end test against a Shopify development shop for discovery → offer → cart → Checkout Kit/ECP → completion/fallback.
- Add security tests for SSRF/endpoint allowlisting, session ownership, token redaction, replay, idempotency, CSP/origin restrictions, and unsafe redirects.
- Emit metrics by provider/integration for latency, timeout, rate limiting, auth refresh, candidate counts, dedupe rate/confidence, source diversity, cart errors, checkout ready time, completion, cancellation, and fallback.
- Add dashboards and alerts for token failures, Global Catalog degradation, checkout failure rate, and completion reconciliation backlog.

**Acceptance criteria**

- CI runs deterministic contract tests without live Shopify credentials.
- A protected scheduled/manual environment runs the real development-shop journey.
- Logs and traces pass a secret/PII leakage test.
- Release owners can identify which provider, merchant, capability, and stage caused a failure.

### PCOS-021 — Roll out Shopify end-to-end behind granular flags

Priority: P0  
Dependencies: PCOS-020  

**Goal**

Release the flow incrementally with independent rollback for discovery, embedded checkout, delegations, and direct completion.

**Work**

- Add environment, percentage, user cohort, and integration-level rollout controls.
- Roll out in this order:
  1. shadow Global Catalog calls and compare coverage,
  2. visible Shopify grouped results,
  3. Shopify cart and checkout session creation,
  4. Checkout Kit/ECP for internal users,
  5. embedded checkout canary,
  6. direct completion canary after permission approval,
  7. individual ECP delegations.
- Define rollback thresholds for latency, dedupe errors, stale offers, cart failures, embedded checkout failures, and completion mismatch.
- Keep external handoff available throughout the rollout.

**Acceptance criteria**

- Each execution rail can be disabled without redeploying or disabling all Shopify discovery.
- Rollback preserves active cart/checkout state and gives the user a safe continuation path.
- Product and engineering sign off against the exit metrics below.

## Phase 5 — Deferred but Designed In

### PCOS-022 — Preserve order-readiness data without building order monitoring yet

Priority: P1 foundation; full orders phase deferred  
Dependencies: PCOS-014, PCOS-018  

**Goal**

Avoid losing the identifiers and consent boundaries required for later order tools and webhooks.

**Work**

- Persist provider, integration, user, cart, checkout, completion, merchant order reference when returned, token audience/scope metadata, and timestamps.
- Ensure order access can later be restricted to purchases placed through Meant.
- Define the future reconciliation key shared by Checkout Kit completion, `complete_checkout`, Order MCP, and webhooks.
- Do not implement polling, webhook subscriptions, order UI, or proactive monitoring in this milestone.

**Acceptance criteria**

- A completed checkout contains enough non-sensitive provenance to attach a later order record deterministically.
- No payment credential or reusable bearer token is stored with the checkout.
- The schema supports multiple merchant orders within one Meant checkout journey.

### PCOS-023 — Stabilize the headless Commerce Runtime and next-provider kit

Priority: P2 after Shopify production proof  
Dependencies: PCOS-021  

**Goal**

Turn the proven internal boundaries into a repeatable integration path and prepare for external agents without prematurely publishing an unstable API.

**Work**

- Extract a documented provider adapter contract for discovery, identity evidence, auth, cart, checkout, embedded checkout, completion, and future orders.
- Create a provider conformance suite that can be run against a new integration.
- Version internal Commerce Runtime commands/events independently from frontend view models.
- Make agent identity and consumer context explicit parameters instead of global assumptions.
- Add service-to-service authorization, tenant/rate-limit boundaries, audit events, idempotency, and usage metering before exposing any external API.
- Validate the kit by integrating the next provider or a realistic fake provider before committing to a public SDK.

**Acceptance criteria**

- The second provider can be added without changing canonical product, cart, or checkout controller contracts.
- The Meant frontend uses the same versioned runtime boundary intended for future clients.
- No public platform launch is required to complete this ticket; the outcome is a proven, documented internal contract.

## Milestone Exit Metrics

Targets must be agreed before rollout; the implementation must at least measure all of them.

- Search time to first useful product and time to final ranked window.
- Search success and partial-source success rate.
- Same-merchant duplicate rate after normalization.
- Product-grouping precision from a human-reviewed evaluation set.
- Percentage of products with multiple valid offers.
- Best-offer override rate and stated user reason where available.
- Price/availability refresh mismatch rate before cart and checkout.
- Cart creation/update success by provider and merchant.
- Checkout Kit ready latency and embedded launch success.
- Checkout start-to-completion conversion.
- Completion reconciliation time and unknown-outcome count.
- External handoff fallback rate and reason.
- Provider rate-limit, auth, and protocol error rates.

## Definition of the First Major Milestone

The Shopify milestone is complete only when a production-like user can search across both Shopify Global Catalog and existing Meant merchants, inspect a grouped product with multiple offers, select a Shopify offer, create and modify the merchant cart, and finish checkout through Checkout Kit/ECP inside Meant with safe reconciliation and an external fallback.

Code readiness for token-authorized `complete_checkout` is part of the milestone. Production enablement depends on Shopify granting the permission. Order monitoring and webhooks follow as the next milestone, but all order-linking provenance must already be captured.

## Official References

Shopify documentation changes frequently. Re-check the current version at the start of each Shopify ticket.

- [Shopify agent quickstart](https://shopify.dev/docs/agents/get-started/quickstart)
- [Authenticate your agent](https://shopify.dev/docs/agents/get-started/authentication)
- [Auth and rate limiting](https://shopify.dev/docs/agents/profiles/auth-and-rate-limiting)
- [About catalogs](https://shopify.dev/docs/agents/catalog)
- [Global Catalog](https://shopify.dev/docs/agents/catalog/global-catalog)
- [Carts and checkout](https://shopify.dev/docs/agents/carts-and-checkout)
- [Checkout Kit](https://shopify.dev/docs/agents/carts-and-checkout/checkout-kit)
- [Embedded Checkout Protocol](https://shopify.dev/docs/agents/carts-and-checkout/ecp)
- [Orders](https://shopify.dev/docs/agents/orders)
