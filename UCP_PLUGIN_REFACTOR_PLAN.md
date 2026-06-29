# UCP Plugin Architecture Refactor — Implementation Plan

## Goal

Refactor Meant from a Shopify-MCP consumer into a 100% UCP-compatible agent, with
every UCP capability implemented as a plugin. Search flow streams results to the FE
over SSE as each plugin resolves. New top-level `plugin/` layer beside `module/`.

## Agreed architecture (decided with CTO in prior discussion)

- **`plugin/` is top-level**, beside `module/`. It is a layer, not a feature.
  - `plugin/spi/` — `UcpCapability`, `CapabilityId`, `NegotiatedCapabilities`,
    `CapabilityAdvertisement`, `UcpToolResponse` (moved from `common/plugin/`).
  - `plugin/catalog/`, `plugin/cart/`, `plugin/checkout/`, `plugin/order/`, … — one
    plugin per UCP capability.
  - `plugin/transport/` — `UcpMcpClient`, `RequestSigner`, `CapabilityRegistry`.
- **Plugin contract is fully typed** — no `JsonNode` on any plugin boundary.
  - `buildArguments(req, active)` returns a typed args record (transport serializes it).
  - `parseResponse(UcpToolResponse)` returns a typed model (plugin converts internally).
  - `toProfileEntry()` returns `List<CapabilityAdvertisement>`.
- **Dispatch is direct** over virtual threads (reuse today's pattern in
  `MerchantSemanticProductSearchService.searchMerchantCatalogs`). Returns values,
  correlates via closure, `allOf` gives completion. NO Spring events on dispatch.
- **BE→FE is SSE.** Spring `ApplicationEventPublisher` is used ONLY at the BE→SSE
  boundary: plugin/orchestrator resolves a result → `publishEvent(ProductResolvedEvent
  (searchId, result))` → SSE listener finds the emitter by `searchId` → sends. Plugin
  never holds an `SseEmitter`.
- **Streaming order:** raw candidates stream immediately as they arrive; rerank lands
  later as a reorder event; FE reorders. First result is fast.
- **Search flow uses full UCP catalog:** `search_catalog` → `lookup_catalog` →
  `get_product` (UCP, not Shopify `get_product_details`).
- **Agent identity is a parameter (`AgentIdentity`)**, not a global singleton, even
  though single-tenant today — keeps the MachineCommerce multi-tenant door cheap.
- **Merchant profile persistence:** columns for filtered fields + `raw` archive
  (JSONB) for extension fields, so the monthly sync never loses unknown fields.

## Data flow (target)

```
FE "running shoes" ──POST /search──► SearchOrchestrator
                                          │
                          embeddings select merchants (UNCHANGED: Voyage embed+rerank)
                                          │
              ┌───────────────────────────┴─────────────── per merchant (virtual thread)
              ▼
   UcpSession (AgentIdentity, cached tools/list + negotiated, cart/checkout state)
              │
   CapabilityRegistry dispatch → catalog.search ─► catalog.lookup ─► get_product
              │                       (each plugin: build typed args, parse typed resp)
              ▼
   UcpMcpClient (meta.ucp-agent.profile, parse structuredContent, [sign for checkout])
              │
   merchant /api/ucp/mcp ──► structuredContent.ucp.capabilities = active set
              │
   result resolved ──► ApplicationEventPublisher.publishEvent(ProductResolvedEvent)
              │
   SSE listener (by searchId) ──► SseEmitter.send  ──► FE renders incrementally
   rerank done ──► ProductsRerankedEvent ──► SSE "reorder" ──► FE reorders
```

## Phases (PR-sized)

### Phase 1 — Plugin SPI + relocate (foundation)
- Create `plugin/spi/` with the 5 contract types (move from `common/plugin/`).
- `CapabilityRegistry` (tool→plugin map, profile generation from enabled plugins).
- `/.well-known/ucp-agent.json` controller serving generated profile.
- `AgentIdentity` from config (profile URL, later signing keys).
- No behavior change to search yet. Unit tests for registry + profile generation.

### Phase 1 — also: agent profile generation (decided D-profile)
- Our agent profile is GENERATED from `List<UcpCapability>` (Spring injects all
  `@Component` plugins) via `CapabilityRegistry`/`AgentProfileProvider`. NOT from .env,
  NOT from DB. Single source of truth = the enabled plugin set.
- Generated ONCE at startup, held immutable in memory, served O(1) at
  `GET /.well-known/ucp-agent.json`. No TTL — plugins don't change without a restart.
- Outgoing requests carry only the profile URL string (from .env) in
  `meta.ucp-agent.profile` — nothing is generated on the hot path.
- `.env` holds: profile URL, protocol version, signing key id. NOT the capability list.

### Phase 2 — UCP transport
- `UcpMcpClient`: sends `meta.ucp-agent.profile` (URL from .env), parses
  `result.structuredContent`, reads `structuredContent.ucp.capabilities` into
  `NegotiatedCapabilities`.
- Extend `McpToolResult` to carry `structuredContent`.
- **Merchant tools/list (decided D2):** DB is source of truth (monthly sync, Phase 8) +
  Caffeine read-through cache at runtime. Cache key = merchant + hash(our agent profile),
  TTL ~1h. On miss: read DB if our profile matches sync profile, else call merchant
  tools/list. DB survives restart (cache loads from DB, not merchant); cache self-heals
  when our profile changes mid-month. [Layer 1: Caffeine + JPA]
- Tests: profile metadata sent, structuredContent parsed, negotiated set extracted,
  endpoint candidate fallback preserved, cache hit/miss/expiry, profile-change invalidation.

### Phase 3 — Catalog plugins + full UCP search
- `catalog.search` (search_catalog), `catalog.lookup` (lookup_catalog),
  `catalog get_product` (UCP get_product), `dev.shopify.catalog` extension.
- Rewire `MerchantSemanticProductSearchService` to dispatch via registry/plugins
  instead of `MerchantCatalogSearchClient` / `MerchantProductDetailsClient`.
- Keep all rich-data / audience / price logic (it consumes plugin output unchanged).
- Tests: each plugin build/parse, negotiation gating, search flow end-to-end.

### Phase 3/4 — also: error isolation (decided D4, IRON RULE regression)
- Each plugin/merchant call wrapped in per-merchant try/catch (preserve today's behavior
  at `MerchantSemanticProductSearchService:194`). One merchant failing = failure event for
  that merchant, others continue. FE may show degraded state ("store unavailable").
- REGRESSION TEST (mandatory, no skip): one merchant throws → other merchants' results
  still stream and the search completes.

### Phase 4 — SSE streaming
- `/search` becomes SSE (`SseEmitter`). SSE emitter registry keyed by `searchId`.
- `ProductResolvedEvent` + `ProductsRerankedEvent` via `ApplicationEventPublisher`.
- Orchestrator emits raw candidates as they resolve; rerank emits reorder.
- **Completion (decided D3):** orchestrator holds `List<Future>` from dispatch (today's
  pattern). Each resolution publishes an event (FE render). `allOf` + `whenComplete`
  (NOT bare join — must run even if a Future throws, so the stream closes on error too)
  waits for all → emit rerank → `emitter.complete()`. Deterministic completion, no hung
  connections.
- FE: consume SSE, render incrementally, reorder without flicker.
- Tests: emitter lifecycle (open/send/complete/error), completion fires even when a
  plugin throws, reorder correctness, FE state.

### Phase 5 — Cart plugins
- `cart`: create_cart, get_cart, update_cart, cancel_cart (full UCP cart).
- Replace today's update_cart-only behavior. Cart state on `UcpSession`.

### Phase 6a — Checkout sessions + handoff (decided D5, no money risk)
- `checkout`: create_checkout, get_checkout, update_checkout.
- Completion via `continue_url` handoff (open Shopify checkout in browser).
- Verifies the entire checkout plumbing with ZERO payment risk. Ships to prod safely.

### Phase 6b — Native completion + signing + AP2 (the hard 80%, money)
- `complete_checkout`, `cancel_checkout`.
- `RequestSigner` (RFC 9421 — [Layer 2: use a library, do not roll signing by hand]),
  idempotency keys (idempotent: safe to retry without double-charging), AP2 mandates,
  buyer_consent.
- Payment handler plugins (Shop Pay / Google Pay / card).
- Built on the verified 6a base. Feature-flagged + canary rollout (highest blast radius).

### Phase 7 — Orders + webhooks
- `order`: get_order. Backend order module. Shopify order webhooks + verification.
- Replace FE mock orders with real merchant order state.

### Phase 8 — Profile persistence fidelity
- Extend monthly sync persistence: keep `raw` JSONB archive of full profile alongside
  normalized columns. Promote new fields to columns when filtered/joined.

## Code quality decisions
- **UcpMoney extraction (decided D6):** the ~250 lines of money/decimal/rating parsing
  in `MerchantSemanticProductSearchService` (`moneyValue`, `decimalAmountToMinor`,
  `currencyExponent`, `normalizeSingleSeparatorDecimal`, lines ~753–1236) move to
  `plugin/spi/UcpMoney` (+ `UcpDecimal`) in Phase 3. Search service uses the shared util;
  cart/checkout/order plugins reuse it. One test owns money parsing. DRY + one source of
  truth for money — critical because checkout builds real payments on it.
- **Delete old clients in-phase (decided D7):** when a plugin replaces a legacy
  `Merchant*Client`, delete the legacy class in the SAME PR. Phase 3 deletes
  `MerchantCatalogSearchClient` + `MerchantProductDetailsClient`; Phase 5 deletes the
  legacy cart flow. No two paths to a merchant, no dead code. (Beck: refactor is part of
  the change.)

## What already exists (reuse, do not rebuild)
- Embeddings + Voyage rerank merchant selection (`MerchantSemanticSearchService`).
- Virtual-thread parallel dispatch pattern (`searchMerchantCatalogs`, `productResults`).
- `Consumer<MerchantSemanticProductResult>` candidate hook — the SSE seam.
- All rich-data/audience/currency logic in `MerchantSemanticProductSearchService`.
- `MerchantMcpToolClient` endpoint candidate + URL validation (extend, don't replace).
- Catalog DTOs (`CatalogSearchCatalog`, `CatalogSearchResponse`) — plugins wrap them.
- Monthly merchant sync (`UcpMerchantImportService`).
- Already-built plugin SPI + `CatalogSearchCapability` snippets (this branch).

## Outside voice (Codex) — accepted corrections

Direction changes (cross-model, user-approved):
- **SSE over POST (D11):** native `EventSource` is GET-only. Use `fetch()` streaming
  (ReadableStream) with a POST body; BE may still use `SseEmitter`/Flux. Stream format
  SSE or NDJSON.
- **Drop ApplicationEventPublisher (D12):** use the existing
  `Consumer<MerchantSemanticProductResult>` seam. Orchestrator holds the `SseEmitter` and
  writes directly as results resolve. This ALSO removes: searchId emitter registry,
  searchId auth ownership checks, per-emitter concurrency hazard, and synchronous-listener
  blocking — all Codex concerns dissolved by this choice.
- **UcpMoney → `plugin/support`, NOT `plugin/spi` (D13):** SPI stays contract-only
  (`UcpCapability` + types). Money/decimal/currency util lives in `plugin/support`.

Held against Codex (cross-model, user-approved — pre-launch context Codex lacked):
- **D7 held:** delete legacy clients in-PR. Pre-launch, no prod traffic — `git revert`
  is the rollback, not parallel dead code. ADD: test each phase against real Allbirds
  endpoint before merge.
- **D2 held:** Caffeine + profile-hash cache. Solves the stale-our-profile problem
  (our profile changes more than monthly during dev); a plain DB lookup would lie.

Codex gaps folded in (no decision needed — straight additions):
- **Cancellation:** FE disconnect must cancel in-flight merchant calls / enrichment /
  rerank (don't burn work on a closed stream). Wire to `SseEmitter` onError/onTimeout +
  `Future.cancel`.
- **Phase 2 ↔ 8 sequencing:** Phase 2 cache reads DB, but DB persistence is Phase 8.
  Fix: Phase 2 ships a minimal persisted tools/list write; Phase 8 extends to full raw
  archive. Phase 2 does NOT depend on Phase 8.
- **Stable product key BEFORE enrichment:** card identity must be durable from the
  `search_catalog` result so later `lookup`/`get_product` enrichment events target the
  same card. Define the key in Phase 3 (reuse today's `productKey`).
- **One async abstraction:** use `CompletableFuture` (not raw `Future`) throughout —
  `allOf` + `whenComplete` + `cancel` need one consistent model with defined exception
  semantics.
- **Async write vs slow client:** orchestrator's direct emitter writes must not block
  merchant worker threads on a slow SSE client — bounded send / drop policy.
- **Timeout/budget policy (Phase 2/3):** explicit per-call, per-merchant, enrichment,
  rerank, and whole-search deadlines. Completion is deterministic only if bounded.
- **Rate/concurrency limits:** virtual threads make it trivial to overload merchants and
  our own client/DB pool — cap concurrent merchant calls.
- **Observability (cross-cutting):** per-merchant latency, failure reason, negotiation
  result, SSE disconnects, timeout metrics.
- **Version metadata on persisted profile (Phase 8):** raw JSONB alone is not enough —
  store captured-at, our-agent-profile-hash, merchant endpoint, protocol version, tool
  list hash, so runtime negotiation can reason about freshness.
- **Partial-merchant failure is not binary (D4 refinement):** a merchant may return
  search results then fail enrichment. FE state is per-card (detail-unavailable), not
  just per-merchant ("store unavailable").
- **Signing is a program, not a line (Phase 6b):** key storage, rotation, canonicalization
  test vectors, clock skew, replay prevention, merchant verification failures — each
  explicit. 6b gets its own threat model before implementation.
- **"100% UCP" needs a definition:** add a conformance checklist (capability list,
  protocol version matrix, required vs optional) as Phase 1 acceptance criteria.
- **Repo rules update:** top-level `plugin/` must be reflected in CLAUDE.md / package
  conventions so implementation reviews don't fight the architecture.

## Performance decision
- **Stream-then-enrich (decided D9):** full UCP catalog is 3 sequential round-trips
  (search_catalog → lookup_catalog → get_product). Do NOT block first render on the
  chain. `search_catalog` result streams a card immediately (title, price, image);
  `lookup_catalog` + `get_product` run async and append detail/variants as a follow-up SSE
  event on that card. Keeps first-result latency low. Fits the stream-then-reorder model.

## Test plan (from coverage review)

REGRESSIONS — mandatory, no skip (IRON RULE):
1. Endpoint candidate fallback preserved after transport refactor (was
   `MerchantMcpToolClient` behavior).
2. One merchant down → other merchants' results still stream + search completes
   (per-merchant isolation, was `MerchantSemanticProductSearchService:194`).
3. SSE completion fires even when a plugin throws (no hung emitter).

UcpMoney — full edge-case suite (decided D8, money correctness):
- zero-exponent currencies (JPY, KRW), 3-decimal (KWD, BHD), EU `1.234,56` vs US
  `1,234.56`, minor-unit hints, grouped thousands, negative/blank, rating normalization.

Per-plugin: build typed args, parse typed response, negotiation gate (Shopify fields
only when `dev.shopify.catalog` active).

Transport: meta.ucp-agent.profile sent, structuredContent parsed, negotiated extracted,
Caffeine hit/miss/expiry + our-profile-change invalidation.

Registry/profile: tool→plugin lookup, profile generated from all plugins, generated once
+ immutable + O(1), unknown tool → error.

SSE: emitter lifecycle (open/send/complete/error), reorder after rerank.

E2E: search query → cards → reorder; cart→checkout→continue_url (6a);
complete_checkout happy + idempotency retry (6b).

## NOT in scope (deferred)
- Multi-tenant public API/MCP for external agents (MachineCommerce productization).
- Lodging / food UCP verticals (shopping only).
- Non-UCP merchant extras (search_shop_policies_and_faqs) beyond fallback.

## Parallelization (worktree lanes)
- Lane A: Phase 1 → 2 → 3 (sequential, shared `plugin/` foundation + transport).
- Lane B: Phase 4 SSE + FE (depends on Phase 3 producing streamable results).
- Lane C: Phase 5 cart → 6a checkout → 6b native (sequential, shared `UcpSession` state).
- Lane D: Phase 7 orders + webhooks (independent backend module, parallel to C).
- Lane E: Phase 8 persistence (touches merchant sync; coordinate with Phase 2 minimal write).
- Launch A first (everything depends on it). Then B + C + D in parallel. E folds in late.
- Conflict flag: Phase 2 (Lane A) and Phase 8 (Lane E) both touch merchant persistence —
  Phase 2 ships the minimal write, Phase 8 extends. Coordinate to avoid migration clash.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | not run |
| Codex Review | `/codex review` | Independent 2nd opinion | 1 | issues_found | 27 raised, 5 cross-model decisions + ~12 gaps folded in |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | 9 issues, 0 critical gaps, 3 mandatory regressions |
| Design Review | `/plan-design-review` | UI/UX gaps | 0 | — | not run (backend-heavy; FE SSE render is minor) |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | not run |

- **CODEX:** 27 points. 5 were cross-model decisions (D11 SSE-over-POST → fetch streaming;
  D12 drop event publisher → Consumer callback; D13 UcpMoney → plugin/support; D7 + D2 held
  with pre-launch rationale). ~12 valid gaps folded straight in (cancellation, timeouts,
  observability, rate limits, stable product key, version metadata, signing-as-program).
- **CROSS-MODEL:** Codex and eng review agreed on isolation, completion determinism, and
  money correctness. Disagreed on event publisher (Codex won, user switched) and on
  delete-in-PR + cache (eng review won on pre-launch context).
- **UNRESOLVED:** 0.
- **VERDICT:** ENG CLEARED — ready to implement. 8 phases (1, 2, 3, 4, 5, 6a, 6b, 7, 8),
  3 mandatory regression tests, full UcpMoney edge-case suite.
