# Backend Refactor Audit

## Goal

This document captures the backend refactor audit findings so we can revisit them
as implementation work is planned. The audit focused on maintainability, database
access patterns, query efficiency, oversized services/controllers, and areas where
the current design is likely to become expensive as data volume grows.

No code changes were made as part of this audit.

## Executive Summary

The highest-value refactors are in the product search/discovery flow, merchant
import/enrichment flow, order/cart persistence flow, and a few oversized services
that currently own too many responsibilities.

The most important potential correctness issue is a cache/profile hash mismatch
between product search persistence and product discovery. That should be clarified
before larger mechanical refactors.

## Highest Priority Findings

### 1. Product discovery likely uses the wrong profile hash

Evidence:

- `UserProductSearchService` builds the persisted search `profileHash` from user
  settings, inventory, and taste profile state.
  - `backend/src/main/java/com/meant/api/module/user/service/UserProductSearchService.java`
- `UserProductDiscoveryService` builds its recent-products lookup hash only from
  user settings.
  - `backend/src/main/java/com/meant/api/module/user/service/UserProductDiscoveryService.java`
- `UserProductSearchPersistenceService.findRecentProducts` filters by exact
  `profileHash`.
  - `backend/src/main/java/com/meant/api/module/user/service/UserProductSearchPersistenceService.java`

Impact:

Discovery may fail to find searches written by the current product search flow,
because the writer and reader do not appear to use the same cache key.

Recommendation:

Make the cache key/profile hash contract explicit. Either discovery must use the
same full hash, or recent-products persistence should intentionally query by a
different stable key. Add tests that cover both a fresh search and discovery
retrieval for the same user.

### 2. Recent product loading has an N+1 query shape

Evidence:

- `UserProductSearchPersistenceService.findRecentProducts` loads recent searches
  and then builds a result from each search.
- For each search, the service loads result items, explanations, and filter
  matches through additional repository calls.

Impact:

The number of database calls grows with the number of recent searches and their
result sizes. This endpoint can become expensive even when the UI only needs a
small recent discovery feed.

Recommendation:

Batch-load result items, explanations, and filter matches by search IDs. Consider
adding a dedicated recent-discovery projection if the UI does not need the full
search result structure.

### 3. Recommendation explanation persistence saves rows one at a time

Evidence:

- `UserProductSearchPersistenceService.saveExplanations` saves each explanation
  separately.
- `saveFilterMatches` saves each filter match separately.

Impact:

A single search can generate many INSERT statements. This is unnecessary database
chatter and will amplify latency under concurrent search usage.

Recommendation:

Build lists of explanation and filter-match entities and persist them with
`saveAll`. If duplicate handling is required, prefer a repository-level upsert or
unique-key-aware insert strategy over per-row existence checks.

### 4. Merchant import scans the full merchant table

Evidence:

- `UcpMerchantImportPersistenceService` calls `merchantRawRepository.findAll()`
  after saving imported merchants and filters inactive merchants in memory.

Impact:

Each import scales with the full `merchant_raw` table. This will become slow and
memory-heavy as merchant count grows.

Recommendation:

Replace the full-table read with a targeted database operation. Options include:

- query active merchants whose domain is not in the current import domain set,
- use a staging/import batch table,
- or perform a targeted bulk update if the active-set semantics are simple.

### 5. Order list endpoint is unpaginated and fetches order lines

Evidence:

- `OrderController.list` returns all orders for the current user.
- `MerchantOrderRepository.findByUserIdOrderByPlacedAtDescCreatedAtDesc` uses
  `@EntityGraph(attributePaths = "lines")`.

Impact:

The list endpoint loads every order and every line item. This is acceptable for
small demos, but it is the wrong shape for production order history.

Recommendation:

Introduce a paginated order summary query that does not fetch lines. Keep line
items on a detail endpoint or a dedicated detail query.

## Medium Priority Findings

### 6. Cart flow performs redundant cart reads around remote calls

Evidence:

- `CartService.get`, `update`, and `checkout` load the cart before remote plugin
  calls.
- `CartPersistenceService.saveSnapshot` and `saveCheckoutHandoff` load the same
  cart again inside persistence methods.

Impact:

The current shape avoids wrapping remote I/O in a long database transaction, which
is good. However, it also performs repeated full entity reads.

Recommendation:

Keep transaction boundaries short, but make the flow explicit:

- load only the pre-call state needed for dispatch,
- perform remote I/O outside a transaction,
- update using a short persistence method that avoids unnecessary full reloads
  where possible.

### 7. User inventory import performs per-item lookup and save

Evidence:

- `UserInventoryService.importPurchasedItems` checks quota once, then for each
  purchased item performs `findByUserIdAndSourceAndSourceProductKey` and `save`.

Impact:

This is manageable while inventory quota is low, but checkout imports can still
generate many avoidable database roundtrips.

Recommendation:

Bulk-load existing inventory rows by source product keys and update/create them
in memory before a single `saveAll`.

### 8. Taste profile signal writes are per-signal upserts

Evidence:

- `UserTasteProfileService.upsertSignal` performs one lookup and one save per
  signal.
- The database already has a unique key for `(user_id, signal_type, signal_key)`.

Impact:

Repeated taste updates, suggestion accept/reject operations, and product-signal
recording can produce many small queries.

Recommendation:

Move toward batch upsert semantics. Use the unique key to simplify conflict
handling instead of performing per-row existence checks in service code.

### 9. Merchant retrieval embedding job has N+1 reads

Evidence:

- `MerchantRetrievalEmbeddingService.generateRetrievalEmbeddings` iterates
  merchants and checks the embedding for each merchant individually.
- `MerchantRetrievalContentBuilder.build` loads categories and popular searches
  per merchant.

Impact:

Embedding refresh cost grows as several queries per merchant. This is especially
noticeable for scheduled/batch jobs.

Recommendation:

Batch-load embeddings, categories, and popular searches for the merchant IDs in
the current job batch.

### 10. User settings repeatedly reload small reference data

Evidence:

- `UserSettingsService` loads active filter IDs multiple times during update
  flows.
- The response builder reloads the full shopping-filter catalog.

Impact:

The cost is small, but the pattern is noisy and easy to multiply across common
user settings endpoints.

Recommendation:

Load active filter IDs once per operation. Consider caching the stable filter
catalog or using a small read model if the catalog changes rarely.

### 11. Merchant enrichment replaces child rows wholesale

Evidence:

- `MerchantEnrichmentPersistenceService.replaceChildren` deletes and recreates
  merchant service/capability/payment/category/popular-search children after a
  profile change.

Impact:

This is simple and reliable, but it creates write churn even when only a subset
of child data changed.

Recommendation:

Keep replace-all semantics if profile updates are rare and low-volume. If monthly
syncs become expensive, refactor to diff child collections and update only changed
groups. Also verify indexes for the unprocessed-active merchant query shape.

## Oversized Classes And Responsibility Boundaries

### MerchantSemanticProductSearchService

File:

- `backend/src/main/java/com/meant/api/module/merchant/service/MerchantSemanticProductSearchService.java`

Approximate size:

- 1200+ lines

Current responsibilities include:

- user search orchestration,
- merchant catalog dispatch,
- parallel remote calls,
- reranking,
- product detail enrichment,
- rich catalog normalization,
- audience/filter matching,
- metadata flattening.

Recommendation:

Split into focused collaborators:

- search orchestrator,
- merchant catalog search executor,
- product detail enricher,
- rich catalog normalizer,
- filter/audience matcher.

This should be done after the cache-key and persistence-query behavior is
clarified, so the refactor can be covered by meaningful behavior tests.

### UserController

File:

- `backend/src/main/java/com/meant/api/module/user/controller/UserController.java`

Approximate size:

- 1000+ lines

Current responsibilities include:

- profile endpoints,
- settings endpoints,
- taste profile endpoints,
- product search/discovery endpoints,
- inventory endpoints,
- saved product endpoints,
- SSE session implementation.

Recommendation:

Split by user subdomain:

- user profile/settings controller,
- taste profile controller,
- product search/discovery controller,
- inventory controller,
- saved products controller.

Move the SSE session helper out of the controller into a support/service class.

### NativeCheckoutCompletionService

File:

- `backend/src/main/java/com/meant/api/plugin/checkout/common/service/NativeCheckoutCompletionService.java`

Approximate size:

- 800+ lines

Observation:

This service is large, but the transaction/remote-I/O separation appears more
deliberate than in some other areas. It uses state/idempotency stores instead of
one large transaction.

Recommendation:

Treat this as a maintainability refactor rather than a first performance target.
Potential boundaries:

- state transition orchestration,
- AP2 mandate/signing,
- completion status interpretation,
- canary/observability recording.

### MerchantCartPluginDispatchService

File:

- `backend/src/main/java/com/meant/api/plugin/cart/common/service/MerchantCartPluginDispatchService.java`

Approximate size:

- 500+ lines

Observation:

This is mostly plugin/transport adaptation, not database-heavy code.

Recommendation:

Split only when changing cart plugin behavior. Good candidate boundaries are
legacy argument adaptation, response parsing, and error interpretation.

## Suggested Refactor Order

1. Clarify and fix the product search/discovery `profileHash` contract.
2. Batch recent product loading in `UserProductSearchPersistenceService`.
3. Batch recommendation explanation and filter-match persistence.
4. Add paginated order summaries and stop fetching lines in the order list.
5. Replace merchant import full-table scanning with targeted database operations.
6. Batch user inventory imports and taste profile signal writes.
7. Batch merchant retrieval embedding job inputs.
8. Split `MerchantSemanticProductSearchService` after behavior is covered.
9. Split `UserController` by subdomain.
10. Split `UserAssistantChatService` into persistence, routing, prompt, and
    response-generation collaborators.

## Testing Strategy For Refactor Work

Recommended focused tests:

- product search writes can be discovered by product discovery with the expected
  profile/cache key,
- recent products load the same response with batched queries,
- recommendation explanation persistence saves explanations and filter matches
  correctly in bulk,
- order list returns paginated summaries without loading lines,
- merchant import deactivates missing merchants without reading the full table,
- inventory imports update existing rows and create new rows in one batch,
- taste profile batch upsert handles existing and new signals,
- merchant embedding refresh avoids per-merchant repository access where possible.

For oversized class splits, prefer characterization tests first. The goal should
be behavior-preserving extraction before changing business logic.
