# Commerce Agent — Implementation Plan

Status: Proposed
Last updated: 2026-07-18
Primary milestone: Working Shopify shopping agent that prepares merchant carts and Embedded Checkout Protocol handoffs

## Purpose and precedence

This plan replaces Meant's client-side chat routing and mandatory search-qualification experience with a real server-side commerce agent. The agent will reason over typed Meant application tools, reuse the commerce capabilities that already work, prepare carts and checkouts, and hand the final checkout to the user through Embedded Checkout Protocol (ECP).

This plan is the source of truth for conversational orchestration, agent state, model tool use, shopping missions, and the Discover chat migration. It is complementary to:

- `plans/personal-commerce-os-implementation-plan.md`, which remains authoritative for provider-neutral discovery, exact offers, carts, checkout, and provider execution.
- `plans/ucp-plugin-refactor-plan.md`, which remains technical background for merchant-facing UCP protocol capabilities.

If this plan conflicts with the current client-side intent router, product-search qualification gate, or client-owned conversation snapshot, this plan takes precedence for the new agent experience.

## Target outcome

A user can have a normal shopping conversation in which Meant:

1. Understands an open-ended shopping goal without treating the word `buy` as an instruction to display an empty checkout.
2. Uses preferences, inventory, orders, prior conversation, and products already shown as context.
3. Searches Shopify through the existing provider-neutral catalog services.
4. Runs several searches when a goal requires several products, such as an outfit or picnic.
5. Discusses alternatives and understands follow-ups such as "the second one," "same size," or "the shoes I own."
6. Selects exact server-issued offers and creates or updates the correct merchant-scoped carts.
7. Prepares each merchant checkout.
8. Presents the existing ECP checkout UI so the user finishes the purchase in the merchant-controlled checkout.
9. Adds the checkout snapshot to inventory when the merchant checkout actually opens in ECP, according to the explicit product rule below.

The first release is Shopify-only at the provider edge. Agent tools remain provider-neutral so adding another UCP provider does not require redesigning the agent.

## Product behavior decisions

### Inventory attribution rule

For this product stage, opening the merchant checkout is treated as a purchase for inventory purposes. This is an intentional testing-oriented product rule, even if the user later abandons the merchant checkout.

The precise trigger is the Checkout Kit/ECP `checkout:start` event: the merchant checkout surface has actually started. The following events do **not** add inventory:

- creating or reading a remote checkout,
- refreshing checkout state,
- updating buyer, address, discount, or fulfillment data,
- mounting the embedded-checkout component,
- preparing an ECP bootstrap session,
- Checkout Kit becoming ready,
- attempting to call `open()` before the SDK confirms `checkout:start`,
- an unavailable, waiting, or failed checkout route.

Reopening, refreshing, remounting, receiving `checkout:start` twice, or later verifying completion for the same logical checkout attempt must not increment inventory again.

The first release applies this early attribution only to confirmed ECP start. External URL fallback attribution is deferred because the browser cannot prove that the merchant surface started. Verified native completion may continue to attribute inventory, but native completion is not exposed as an agent tool in this milestone.

### Agent purchase boundary

The agent can autonomously:

- inspect user context,
- inspect inventory and orders,
- search, retrieve, compare, and explain products,
- construct shopping missions and checklists,
- select offers when the user instruction is sufficiently clear,
- create and update merchant-scoped carts,
- prepare checkout sessions.

The agent does not call `complete_checkout` in the first release. It returns a checkout-ready artifact and the existing ECP surface. The user performs the final merchant checkout interaction.

### Preserve the working commerce runtime

This is a new orchestration layer above the functional product, not a replacement for it. Reuse:

- federated catalog discovery,
- query preparation where useful,
- canonical product grouping and ranking,
- stable product and offer references,
- product rehydration,
- inventory, preferences, taste signals, saved items, and order services,
- exact-offer cart validation and merchant routing,
- checkout creation, update, execution policy, consent, reconciliation, and ECP,
- Shopify provider and UCP transport implementations,
- product cards, grouped product details, cart blocks, checkout blocks, and Checkout Kit UI.

The language model never receives provider credentials and never calls Shopify clients, provider classes, or the UCP capability registry directly.

### One commerce agent, not an agent swarm

Use one primary shopping agent with deterministic Meant tools. Shopping missions and tool results provide structure. Do not introduce multiple specialist agents unless measured evaluations later show a concrete need.

### Preserve the current product design and interactions

The current product-result design is a product contract. Agent-found products must render through the same product batch, product card, grouped-product modal, pagination, pricing, merchant, badge, and action components used today. The agent layer supplies grounded data and actions to these components; it does not replace them with a generic text list or a new agent-specific card design.

Every product CTA that is visible in the current design remains visible and functional, including:

- Add to cart,
- Pin and unpin,
- Watch and unwatch,
- Reviews,
- Find a code,
- Similar,
- Compare and comparison selection,
- Just pick one for me where it is currently offered.

A direct click remains a deterministic UI action and must work without asking the model for permission or interpretation. The click handler and the equivalent agent tool call use the same underlying application service and exact product/offer references. A successful direct action is persisted as a typed `USER_ACTION` conversation message with its artifact references, so the agent understands the updated state on the next turn without creating a model run.

Natural-language actions provide a second route to the same functionality. Examples:

- `Compare the first two` resolves the first two products in the current product artifact, invokes comparison, and renders the existing comparison component.
- `Show me products similar to the third one` invokes the same anchored-similarity capability as the Similar button and renders the normal product cards.
- `Pin the first one` updates the same persistent pin state as the Pin button.
- `Add the gray pair to my cart` resolves and revalidates the exact offer, then invokes the existing cart behavior.

If a currently visible CTA is backed by placeholder or simulated behavior, keep its current component and design but replace the placeholder with a real application capability before the agent experience is considered complete. Do not remove the CTA to simplify the agent migration.

## Why the current architecture must change

The current Discover surface is a keyword router plus a fixed qualify-then-search pipeline:

- `ChatDiscoverView.submit` maps any text containing `buy`, `pay`, or `checkout` directly to a local checkout block.
- An active qualification ID forces every reply back through product-search qualification.
- Qualification mandates numerous filters even when they do not materially block a useful search.
- The current OpenRouter client sends one system message and one user message and cannot represent model tools, tool calls, tool results, or a multi-step run.
- Conversation history is stored as client-authored opaque `threadJson`; it is not a durable server-owned transcript or tool ledger.
- Inventory is used only indirectly during ranking and cannot reliably anchor "the shoes I own."

The result looks conversational but cannot decide to inspect inventory, execute several catalog searches, compare results, modify a cart, and return to the conversation.

## Terminology

- **Commerce agent:** Meant's application-level reasoning and orchestration layer.
- **Agent tool:** A typed, authenticated wrapper around a Meant module service.
- **UCP capability:** Merchant protocol infrastructure under `plugin`; it is not a model tool.
- **Conversation:** Durable user/assistant history.
- **Run:** One attempt to handle a user turn, including model calls and tool invocations.
- **Shopping mission:** Durable structured state for a goal that may require several items, such as an outfit or picnic.
- **Artifact reference:** A stable reference from conversation content to a product, offer, inventory item, cart, or checkout.
- **Checkout attempt:** One stable local identity for a logical checkout, surviving refreshes and ECP session recreation until cart mutation invalidates it.

## Framework decision

### Decision

Use **Spring AI 2.0 GA** for model transport, OpenAI-compatible messages, tool schemas, tool-call parsing, streaming aggregation, and Micrometer observations. Use its user-controlled tool execution mode.

Meant owns:

- the run loop,
- durable conversations and complete history,
- tool availability and authorization,
- mutation idempotency,
- time, step, token, and repeated-call limits,
- cancellation and recovery,
- checkout policy,
- event persistence and frontend streaming.

Do not use a framework's in-memory chat memory or automatic agent executor as the source of truth.

### Why Spring AI

- The backend is already Spring Boot 4.1, Java 25, Actuator, JPA, PostgreSQL, and Liquibase.
- Spring AI 2.0 is GA and was designed for Spring Boot 4.0/4.1.
- It supports automatic, advisor-controlled, and fully user-controlled tool execution.
- Its documented manual streaming loop can forward partial responses while aggregating tool-call fragments.
- It provides Spring-native model and tool observations, with tool arguments/results excluded from telemetry by default.
- Its OpenAI model supports a configurable base URL and can remain pointed at OpenRouter.

Do not let Spring AI's automatic tool loop own commerce execution. Commerce execution remains explicitly controlled by Meant.

### Why not extend the custom OpenRouter client into a private framework

The existing one-shot client remains in place for current classifiers and structured-output services during migration. The new agent should not extend it to independently implement streaming tool-call fragments, parallel calls, cancellation, usage, finish reasons, provider normalization, and telemetry. Spring AI handles that protocol layer while the agent module owns business behavior.

### Primary framework references

- [Spring AI 2.0 GA and Spring Boot 4.1 alignment](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/)
- [Spring AI user-controlled and streaming tool execution](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI OpenAI-compatible configuration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI observability](https://docs.spring.io/spring-ai/reference/observability/index.html)
- [Spring AI memory versus complete history](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)

## Target architecture

```text
React chat
  -> create user turn
  -> subscribe to persisted run events
      -> module.agent run coordinator
          <-> Spring AI ChatModel through OpenRouter
          -> agent policy + tool registry
              -> user context tools
              -> catalog tools
              -> cart tools
              -> checkout preparation tools
                  -> existing module services
                      -> existing provider/UCP runtime
                          -> Shopify first
```

### Package boundary

Create a top-level business module following repository layering:

```text
com.meant.api.module.agent
  controller
    request
    response
  service
    command
    query
    dto
    task
    port
  repository
  entity
  constant
  exception
  properties
```

The module owns business conversation and run state. Agent tool adapters and orchestration classes remain in the flat `service` package; tool inputs/outputs live in `service.dto`. `service.port` is used only for the external model-gateway inversion boundary. Model transport configuration belongs in `common` infrastructure or behind that module-owned gateway contract. `module.agent` may call public services, commands, queries, DTOs, and ports from other modules. It must not import concrete providers or module repositories/entities.

## Runtime model

### Turn API

Use a reconnectable conversation/run protocol:

1. Conversation lifecycle:
   - `POST /api/v1/users/me/agent/conversations` creates a conversation.
   - `GET /api/v1/users/me/agent/conversations` lists the user's conversations.
   - `GET /api/v1/users/me/agent/conversations/{conversationId}` returns metadata plus a paged transcript/artifact snapshot.
   - `PATCH /api/v1/users/me/agent/conversations/{conversationId}` renames or archives it.
2. `POST /api/v1/users/me/agent/conversations/{conversationId}/turns`
   - validates ownership,
   - persists the user message,
   - creates one run,
   - returns `202 Accepted` with `runId` and the first event cursor.
3. `GET /api/v1/users/me/agent/runs/{runId}/events?after={cursor}`
   - returns `text/event-stream`,
   - replays persisted events after the cursor,
   - follows the active run until a terminal or waiting state.
4. `GET /api/v1/users/me/agent/runs/{runId}` returns a recovery snapshot when the event cursor has expired.
5. `POST /api/v1/users/me/agent/runs/{runId}/cancel`
   - requests cancellation and prevents new tools from starting.

Reuse the existing WebMVC `SseEmitter`, bounded queue, virtual-thread, cancellation, and cleanup patterns. Do not add WebFlux solely for the agent stream.

`WAITING_FOR_USER` ends execution and releases the conversation lock. A clarification is a new user turn and a new run linked to the same conversation and mission; the old run is not resumed in memory.

If the user submits while a run is executing, use cancel-and-queue semantics: persist the new user message and a `QUEUED` replacement run, mark the current run cancellation-requested, finish or reconcile any tool already in flight, then start the replacement. Only one run executes at a time. Stop performs the same cancellation without creating a replacement run.

### Run loop

For every run:

1. Acquire the per-conversation execution lock; only one run may execute for a conversation.
2. Persist `RUN_STARTED`.
3. Load recent messages, a rolling summary, active mission, artifact index, and only the user context relevant to this turn.
4. Select a per-turn tool allowlist.
5. Send the bounded conversation and tool definitions to the model.
6. Stream assistant text events while aggregating the complete model response.
7. If the model proposes tools:
   - persist each proposal,
   - validate schema, ownership, policy, and references,
   - reserve an idempotency key for mutations,
   - execute read tools concurrently where safe,
   - execute mutations serially,
   - persist redacted results and referenced domain IDs,
   - emit tool and artifact events,
   - send tool results into the next model iteration.
8. Stop on a final assistant answer, cancellation, configured limit, repeated-call loop, deadline, or error.
9. Persist the terminal state before closing the event stream.

All remote catalog/cart/checkout calls happen outside database transactions. Persistence before and after a remote mutation uses short transactions and stable idempotency keys.

### Configurable guardrails

Add configuration properties for:

- enabled/rollout state,
- model and fallback model,
- temperature and maximum output tokens,
- maximum model iterations,
- maximum total and per-tool invocations,
- maximum parallel read tools,
- run wall-clock deadline,
- per-tool deadline,
- context token/message budget,
- result-size limits,
- repeated identical tool-call threshold,
- event-stream timeout and queue size.

No default values belong in the configuration-properties record; all values come from `application.yml`.

## Persistence model

Add Liquibase XML migrations and typed JPA entities for:

### `AgentConversation`

- user ownership,
- title and status,
- rolling summary and summary version,
- active mission reference,
- last sequence number,
- created/updated timestamps.

### `AgentMessage`

- conversation and optional run,
- role: `USER`, `USER_ACTION`, `ASSISTANT`, `TOOL`, `SYSTEM_SUMMARY`,
- ordered sequence,
- typed content kind,
- text content or bounded serialized typed content,
- model/tool correlation ID,
- created timestamp.

### `AgentRun`

- conversation and triggering user message,
- status: `QUEUED`, `RUNNING`, `WAITING_FOR_USER`, `COMPLETED`, `FAILED`, `CANCELLED`,
- model and prompt version,
- iteration/tool counts,
- token usage where supplied,
- failure code and safe message,
- started/completed timestamps,
- optimistic version.

### `AgentToolInvocation`

- run and model tool-call ID,
- tool name and version,
- risk class,
- status,
- bounded argument/result payloads,
- idempotency key for mutations,
- latency and failure classification,
- resulting product, offer, inventory, cart, or checkout references,
- timestamps.

### `AgentRunEvent`

- run,
- monotonically increasing cursor,
- event type,
- bounded typed payload,
- timestamp.

This makes reconnect and history deterministic; SSE is a projection of stored events rather than the only record.

Every event uses a versioned discriminated envelope containing `schemaVersion`, `cursor`, `conversationId`, `runId`, `type`, `occurredAt`, and a type-specific payload. V1 event types are:

- `run.started`
- `assistant.delta`
- `assistant.completed`
- `tool.proposed`
- `tool.started`
- `tool.completed`
- `tool.failed`
- `artifact.upserted`
- `cart.changed`
- `checkout.ready`
- `run.waiting_for_user`
- `run.completed`
- `run.failed`
- `run.cancelled`

The client ignores unknown future event types, deduplicates repeated cursors, and applies events only in cursor order. A cursor older than retained history returns `410 Gone` with instructions to load the run/conversation snapshot and continue from its latest cursor. Authorization failures never open a stream. Terminal state is persisted before its terminal event is published. Disconnect does not cancel a run; explicit Stop or a replacement turn does. Tests cover reconnect, duplicate delivery, retention expiry, cancel/disconnect races, and terminal replay.

### `ShoppingMission`

- conversation and user,
- goal and status,
- typed assumptions,
- requirements/checklist,
- constraints such as party size, occasion, dietary requirements, date, and budget,
- selected alternatives and coverage state,
- cart and checkout references.

Do not put arbitrary provider payloads into the mission. Persist Meant references and concise facts needed for future turns.

## What conversational reference resolution means

This is not a separate speculative AI subsystem or an extra delivery phase. It is the minimum context needed for the agent to act on normal follow-up language.

Examples:

- After displaying three products, "add the second one" must resolve to the exact canonical product and offer from that product artifact.
- After the user selects an owned pair of shoes, "same size" must resolve to its stored selected size/variant option.
- "The shoes I own" must resolve to one or more inventory item IDs; if several are plausible, the agent shows a compact disambiguation choice.
- "Remove that" must resolve to the exact cart line previously discussed.

Persist an artifact index linking messages and events to `canonicalProductKey`, `offerKey`, `inventoryItemId`, `cartId`, `cartLineId`, and `checkoutAttemptId`. The context assembler gives the model concise labeled references. Every mutation tool accepts server-issued stable IDs and revalidates them; it never trusts a product description invented by the model.

## Agent application tools

Agent tools wrap module services. They are distinct from UCP capability implementations.

### Read-only context tools

- `get_user_preferences`
- `search_inventory`
- `get_inventory_item`
- `list_recent_orders`
- `get_order`
- `list_saved_products`
- `get_active_carts`

Read tools execute automatically and return bounded results. They omit secrets and unnecessary personal data.

### Catalog tools

- `search_catalog`
- `get_product`
- `find_similar_products`
- `compare_products`

`search_catalog` accepts optional constraints. It does not require every current qualification field. Search-first behavior is the default; the agent asks only when an unanswered fact materially changes the next useful action.

`find_similar_products` supports either a recent canonical product reference or an inventory-backed product reference.

### Product interaction tools

- `pin_product`
- `unpin_product`
- `watch_product`
- `unwatch_product`
- `get_product_reviews`
- `find_discount_codes`
- `pick_recommended_product`

These expose the same application capabilities as the existing product-card CTAs. Agent tool results and direct CTA results use the same typed product, similar-results, comparison, reviews, discount, and saved-state artifacts. The model does not generate substitute UI markup.

### Planning tools

- `create_shopping_mission`
- `update_shopping_mission`
- `evaluate_mission_coverage`

These persist structure for multi-item goals. The model proposes requirements; deterministic validation prevents duplicate checklist items and tracks which selected products cover which requirements.

### Cart tools

- `get_cart`
- `prepare_carts`
- `add_cart_line`
- `update_cart_line`
- `remove_cart_line`

`prepare_carts` accepts exact selected offer keys and deterministically partitions them by merchant/provider routing before calling the existing one-merchant-per-cart services. It returns a consolidated result with separate cart IDs.

Cart tools are reversible. They may run without another confirmation when the user clearly instructed the action, for example "add the second one." If selection is ambiguous, the agent asks or presents a proposal instead of guessing.

### Checkout tools

- `prepare_checkout`
- `get_checkout`
- `update_checkout`

These reuse the existing cart and checkout services. `prepare_checkout` returns a typed checkout artifact with the next action. For an embedded rail, the UI renders the existing ECP component.

Do **not** register `complete_checkout`, raw payment tools, Shopify clients, or generic UCP transport calls in the first-release agent registry.

## Phase 1 — Inventory attribution and product identity fix

This phase is completed and released before enabling the new agent.

### AGENT-001 — Introduce a stable checkout-attempt identity

Priority: P0
Dependencies: None

**Goal**

Give every logical checkout a stable local identity that survives remote refreshes, ECP bootstrap retries, and new short-lived embedded session IDs.

**Work**

- Add `checkoutAttemptId` and its creation timestamp to cart checkout state.
- Generate it when a remote checkout is first established.
- Preserve it across get/update/bootstrap/reconcile operations.
- Invalidate it when a cart mutation invalidates the active checkout; a newly established checkout receives a new attempt ID.
- Return the current attempt ID or an opaque attempt token in the embedded bootstrap contract for stale-event protection.
- Never use the short-lived embedded session ID, remote update timestamp, or browser-supplied product data as purchase identity.

**Acceptance criteria**

- Refreshing the same checkout preserves `checkoutAttemptId`.
- Preparing a second embedded session for the same checkout preserves it.
- A cart mutation that requires a new checkout invalidates the old attempt.
- Events for an old attempt cannot attribute the current cart.

### AGENT-002 — Add idempotent checkout-to-inventory attribution

Priority: P0
Dependencies: AGENT-001

**Goal**

Import the server-side cart snapshot once per checkout attempt, regardless of duplicated callbacks or retries.

**Work**

- Add a checkout-owned attribution/ledger entity and repository with a unique constraint on `(user_id, checkout_attempt_id)`.
- Add a typed `RecordCheckoutOpenedCommand` and `CheckoutPurchaseAttributionService`.
- Under a short transaction and cart lock:
  - verify authenticated ownership,
  - verify the attempt is current,
  - snapshot server-side cart lines,
  - reserve the unique attribution,
  - project the lines into user inventory,
  - commit the attribution and inventory changes atomically.
- Record rail and trigger as audit metadata, not as part of the uniqueness key.
- Use the first successful attribution time as `purchasedAt`; do not use changing remote/cart refresh timestamps.
- A uniqueness race becomes a successful no-op.
- Keep verified native completion as an idempotent fallback through the same attribution service.

**Acceptance criteria**

- First confirmed ECP start imports the checkout snapshot.
- Repeated start, refresh, remount, new embedded session, completion verification, or response-lost retry does not change quantity.
- A different checkout attempt can add another purchase quantity.
- A wrong user or stale attempt is rejected.
- Rollback leaves neither a ledger row nor partial inventory and a retry succeeds.

### AGENT-003 — Move the trigger to confirmed ECP start

Priority: P0
Dependencies: AGENT-002

**Goal**

Match the explicit product rule: inventory is added when the merchant ECP surface actually starts.

**Work**

- Remove inventory import calls from checkout read/create/refresh/update and failed completion-verification paths in `CartService`.
- Add an authenticated idempotent endpoint such as:
  - `POST /api/carts/{cartId}/checkout/embedded/{sessionId}/opened`
- The endpoint accepts no product lines or quantity data.
- Validate the active embedded session binding, user, cart, checkout, origin, and current attempt.
- Checkout Kit emits the `checkout:start` `CustomEvent`; `checkoutKitAdapter.ts` maps it to Meant's `onStart` callback. Call the endpoint from that mapped callback only.
- Retry transport failures with the same semantic attempt; never create a second purchase identity.
- Successful provider-verified completion calls the same attribution method as a fallback if the start acknowledgment was lost.
- Make start attribution win a start/close race: record the session's start acknowledgment idempotently, and allow a confirmed current-attempt start to finish attribution if close/unmount cancellation arrives immediately afterward. Cancellation must not erase or invalidate an already-confirmed start.
- Do not import on bootstrap, `onReady`, `open()` attempt, cancellation, or external fallback.

**Acceptance criteria**

- Navigating into the checkout UI without opening ECP leaves inventory unchanged.
- Clicking Open but failing before `checkout:start` leaves inventory unchanged.
- `checkout:start` adds inventory once and the item is immediately visible for testing.
- Closing or abandoning the merchant checkout does not remove the item; this is the chosen product behavior.
- Opening the same checkout again keeps the same quantity.

### AGENT-004 — Preserve commerce identity and selected options in inventory

Priority: P0
Dependencies: AGENT-002

**Goal**

Make inventory sufficiently grounded for "find shoes similar to ones I own" and "same size."

**Work**

- Extend the purchase-import command and inventory persistence with typed product reference data:
  - provider,
  - merchant integration and external merchant identity,
  - canonical/offer key where available,
  - external product and variant identity,
  - selected options such as size and color,
  - image and product URL,
  - source checkout attempt.
- Populate these values from server-side `CartLine`; do not round-trip raw browser data.
- Model selected options with concrete service DTOs and expose typed option fields in inventory responses.
- Preserve graceful fallback for legacy/manual/photo inventory without commerce identity.
- Add an inventory-to-canonical-product rehydration service that uses existing provider-neutral catalog boundaries.

**Acceptance criteria**

- A purchased shoe inventory item retains its exact selected size and variant.
- The same item can be used as a similarity anchor without searching for its title first.
- Legacy inventory remains readable and can still use name/category fallback.
- No module imports a concrete Shopify class.

### Phase 1 tests

- Update `CartServiceTest`: checkout read/create/refresh/update do not import inventory.
- Add `CheckoutPurchaseAttributionServiceTest`: first attribution, duplicate callback, concurrent duplicate, new embedded session, completion fallback, stale attempt, wrong user, new attempt, and rollback/retry.
- Update `EmbeddedCheckoutBootstrapServiceTest`: bootstrap, wait, unavailable, and failed verification do not attribute.
- Add controller integration coverage for authentication, ownership, stale attempts, and repeated opened acknowledgments.
- Update `UserInventoryServiceTest`: same attempt never increments; a distinct attempt can accumulate.
- Add frontend tests proving bootstrap/onReady/open-attempt do not acknowledge and the adapter-mapped `checkout:start` does.
- Add a start/close race integration test proving immediate close/unmount cannot cancel a confirmed start before its one inventory attribution commits.
- Extend the ECP test runbook with immediate inventory visibility and reopen/no-duplicate checks.

## Phase 2 — Build and ship the complete agentic layer

Phase 2 is one end-to-end product milestone. The tickets below are implementation slices, not separately shippable substitute experiences. The new Discover agent is not released broadly until the complete request-to-ECP journey and the acceptance scenarios pass.

### AGENT-101 — Add the model gateway and fake-model harness

Priority: P0
Dependencies: Phase 1 complete

**Goal**

Prove tool calling through OpenRouter without coupling business orchestration to a framework or provider DTO.

**Work**

- Import the pinned Spring AI 2.0 GA BOM and only the required GA OpenAI/model modules.
- Configure a dedicated agent model, base URL, key, headers, timeouts, and fallback model from `application.yml`.
- Point the OpenAI-compatible model at OpenRouter.
- Define a narrow module-owned `AgentModelGateway` contract.
- Disable automatic tool execution and expose complete/streaming model turns to the Meant run coordinator.
- Keep the existing `OpenRouterChatClient` untouched for existing one-shot paths.
- Add a deterministic fake model that returns scripted assistant text and tool calls for tests.
- Verify one text response, one tool call/result round trip, multiple sequential calls, streamed tool arguments, cancellation, timeout, and unsupported-model behavior.

**Acceptance criteria**

- No agent service imports Spring AI provider-specific DTOs outside the gateway adapter.
- A fake model can test every agent path without network access.
- A configured OpenRouter model successfully selects and consumes a harmless test tool.
- Model and tool observations appear in Actuator/Micrometer without arguments or results by default.

### AGENT-102 — Persist conversations, runs, tools, events, and missions

Priority: P0
Dependencies: AGENT-101

**Goal**

Create server-owned, resumable agent state before any commerce mutation is exposed.

**Work**

- Add the entities, repositories, enums, Liquibase XML, commands, queries, and DTOs described above.
- Enforce authenticated ownership on every query and command.
- Enforce one active run per conversation with locking/optimistic versioning.
- Store bounded full history separately from the prompt-memory projection.
- Add prompt version, tool version, and model metadata.
- Add event replay cursors and retention policy.
- Add cancellation and stale-run recovery.

**Acceptance criteria**

- Restarting the application does not lose completed or failed run history.
- A disconnected client can resume after its last cursor.
- Two simultaneous turns cannot mutate the same conversation concurrently.
- Tool calls and results remain auditable without relying on framework memory.

### AGENT-103 — Implement the controlled run loop and policy

Priority: P0
Dependencies: AGENT-102

**Goal**

Execute bounded multi-step model/tool runs safely.

**Work**

- Implement context assembly, tool allowlisting, model iteration, tool dispatch, event emission, terminal handling, and retry classification.
- Register typed tools through adapters; do not annotate existing business services as unrestricted tools.
- Automatically execute read tools.
- Execute cart mutations only when the current user turn or active mission makes the target unambiguous.
- Reject invented/stale identifiers before calling a domain service.
- Reserve stable idempotency keys before remote mutations.
- Add maximum steps, deadlines, repeated-call detection, output truncation, cancellation, and safe errors.
- Treat model text as presentation, never authorization or proof that a mutation succeeded.

**Acceptance criteria**

- A run can call several read tools and one mutation, then produce a grounded final response.
- A looping model terminates safely with a user-facing recovery message.
- Cancellation prevents subsequent tools from starting.
- A model cannot call an unregistered tool or access another user's identifier.

### AGENT-104 — Add user, inventory, order, and artifact context tools

Priority: P0
Dependencies: AGENT-103

**Goal**

Let the agent understand what the user owns, prefers, has discussed, and has already seen.

**Work**

- Implement the read-only context tools.
- Build the artifact index for products, offers, inventory items, carts, and checkouts.
- Return concise numbered references rather than dumping full datasets into the prompt.
- Add deterministic reference validation for follow-up mutations.
- Use recent order/inventory facts only when relevant to the turn.

**Acceptance criteria**

- "The shoes I own" returns grounded inventory candidates.
- "Same size" resolves the selected option from the chosen inventory item.
- "The second one" resolves the second product in the cited artifact, not a new search.
- Ambiguous owned items cause one compact disambiguation response.

### AGENT-105 — Add catalog, detail, similarity, and comparison tools

Priority: P0
Dependencies: AGENT-104

**Goal**

Make existing catalog functionality agent-selectable without the current mandatory qualification gate.

**Work**

- Wrap grouped product search, product rehydration/detail, inventory-anchored similarity, and comparison.
- Wrap pin/watch/reviews/discount-code capabilities so conversational actions and direct product-card CTAs share the same application behavior.
- Allow useful searches with partial constraints.
- Let the agent state reasonable assumptions and search first.
- Ask at most one high-value question before the first useful proposal unless execution is impossible without more data.
- Run independent catalog reads concurrently within configured limits.
- Persist product artifacts and exact offer references returned to the frontend.

**Acceptance criteria**

- "I wanna buy new clothes" produces useful clothing options and never an empty checkout.
- A broad request reaches a first useful proposal with no more than one clarification.
- Similar-shoes results are anchored to an actual inventory item and preserve size context.
- Clicking Similar, Pin, Watch, Reviews, Find a code, Compare, or Add to cart invokes its real capability and keeps the current product-card presentation.
- Typing `compare the first two`, `pin the first one`, or `show similar to the third` resolves the same displayed product references and renders the same existing components as a click.
- Search/provider failures produce partial grounded results or a scoped explanation.

### AGENT-106 — Add shopping missions and multi-item planning

Priority: P0
Dependencies: AGENT-105

**Goal**

Support goals that are not a single catalog query.

**Work**

- Persist and update mission requirements, assumptions, alternatives, and coverage.
- Let the agent infer a checklist for an outfit or picnic.
- Ask only for facts with high expected impact, such as party size or dietary restriction.
- Inspect inventory and mark already-covered needs.
- Run multiple catalog searches and associate selected products with requirements.
- Keep planning provider-neutral and deterministic after the model proposes the structure.

**Acceptance criteria**

- "Plan a summer picnic in San Francisco" produces a coherent checklist and product bundle.
- Missing party size/dietary information is handled through one compact clarification or stated assumptions.
- The mission identifies which requirement each product covers.
- Already-owned relevant items can be excluded from the buy list.

### AGENT-107 — Add exact-offer cart tools and multi-merchant coordination

Priority: P0
Dependencies: AGENT-106

**Goal**

Turn a conversation or mission into correct merchant-scoped carts using existing cart services.

**Work**

- Implement cart read/add/update/remove and `prepare_carts` tool wrappers.
- Require server-issued offer keys and perform existing rehydration/revalidation.
- Partition selected offers by routing scope into separate carts.
- Persist tool idempotency before calling remote mutations.
- Return consolidated cart artifacts plus per-merchant status.
- Stream real cart changes and preserve undo for reversible actions where supported.

**Acceptance criteria**

- "Add the second one" adds the exact second offer once.
- Retrying a mutation after a lost response does not duplicate the line.
- A picnic spanning merchants creates separate merchant carts but one consolidated UI result.
- One merchant failure does not erase successful carts for other merchants.

### AGENT-108 — Prepare checkout and hand off through ECP

Priority: P0
Dependencies: AGENT-107

**Goal**

Complete the agent journey at the existing secure merchant checkout boundary.

**Work**

- Implement checkout get/update/prepare tool wrappers.
- Let the agent prepare checkout when the user says to proceed or when a clearly delegated mission is ready.
- Return one checkout artifact per merchant with the explicit next action.
- Reuse `InlineCheckoutBlock`, `EmbeddedCheckout`, Checkout Kit policy, origin validation, session binding, and completion verification.
- Keep ECP activation sequential in v1 because the current frontend owns one active checkout session. The consolidated artifact lists every prepared merchant checkout, but only one merchant checkout is active at a time; closing or finishing it returns the user to the remaining actions.
- Do not expose direct completion or payment credentials to the model.
- Connect the Phase 1 ECP-start inventory attribution.

**Acceptance criteria**

- The agent can search, discuss, add exact offers, prepare checkout, and render an ECP action without leaving the conversation.
- The user—not the model—opens and finishes the merchant checkout.
- ECP start attributes inventory once under the chosen product rule.
- Multiple merchant carts produce separate ECP checkout actions and clear completion status; activating one does not overwrite the remaining merchants' actions or state.

### AGENT-109 — Replace Discover orchestration while reusing leaf UI

Priority: P0
Dependencies: AGENT-102 and AGENT-103 for the shell; completed alongside AGENT-104 through AGENT-108

**Goal**

Make the frontend an agent-run client and renderer instead of the natural-language decision-maker.

**Reuse as leaf components**

- the existing composer and message-row visual treatment,
- `DiscoverProductBatch`, `ProductCard`, grouped-product modal, pagination, and current product-card layout,
- every existing product CTA and its current placement/interaction design,
- the existing comparison UI,
- inline cart and checkout blocks,
- embedded checkout and merchant handoff UI,
- thread tabs/history visual affordances,
- inventory relationship badges.

**Replace**

- `ChatDiscoverView.submit` regex intent routing,
- `MeantApp.runProductSearch` as the chat orchestrator,
- the mandatory qualification/search turn contract,
- fabricated agent-activity entries,
- canned decision-making behind product actions; preserve its visible CTA and replace any placeholder handler with a real capability,
- client-owned `threadJson` as transcript authority,
- live-state rendering that rewrites historical checkout messages.

**Work**

- Early slice after AGENT-102/103:
  - add authenticated conversation/run API calls and an event-stream reader,
  - add the pure TypeScript versioned event reducer with cursor deduplication, snapshot recovery, and reconnect,
  - back thread tabs with server-owned conversations,
  - implement cancel-and-queue composer behavior and Stop,
  - ship a feature-flagged text/tool-activity agent shell.
- Incremental slices alongside AGENT-104 through AGENT-108:
  - add typed renderers as each artifact/tool contract lands,
  - adapt agent product artifacts to the existing product mapping and component props,
  - reuse product cards, all CTA handlers, comparison, cart, and sequential checkout leaf UI without redesigning them,
  - persist successful direct CTA actions as typed `USER_ACTION` messages so later turns observe them without an LLM round trip,
  - verify each backend tool vertically through the real client.
- Final slice after AGENT-108:
  - remove frontend intent/search orchestration from the enabled agent path,
  - retain the legacy path behind its rollback flag until AGENT-110 passes.

**Acceptance criteria**

- No user sentence is routed by frontend commerce keywords.
- Agent-found products use the current product batch/card/modal design and current CTA layout.
- Every existing CTA works by direct click without an LLM round trip.
- Equivalent natural-language actions call the same underlying capability and render the same component.
- Reload and reconnect reproduce the same transcript and artifacts.
- Tool activity reflects real backend events.
- Historical messages retain their original artifact references.
- Submitting during a run deterministically cancels-and-queues; a clarification after `WAITING_FOR_USER` creates a new run.
- Unknown event versions/types and expired cursors recover without corrupting the transcript.
- Existing non-agent product/cart/checkout routes remain functional during rollout.

### AGENT-110 — Evaluations, observability, rollout, and legacy retirement

Priority: P0
Dependencies: AGENT-109

**Goal**

Prove the new layer is useful and safe before removing the old orchestration.

**Work**

- Add deterministic scenario fixtures and fake-model integration tests.
- Add optional live-model evaluations outside normal CI.
- Measure:
  - time to first useful proposal,
  - clarification count,
  - valid tool-call rate,
  - reference-resolution accuracy,
  - inventory-anchor accuracy,
  - mission coverage,
  - cart mutation and checkout-preparation success,
  - loop termination, latency, token use, and cost.
- Add traces/metrics correlated by conversation, run, model, tool, provider, and merchant without recording sensitive arguments/results.
- Add agent endpoints to rate limits and abuse controls.
- Roll out internally, then by cohort, with a kill switch that returns users to the still-working legacy surface.
- After the agent meets the definition of done, remove the client keyword router and qualification-as-chat gate. Retain useful typed parsing services only where an agent tool still uses them.

**Acceptance criteria**

- All north-star scenarios pass deterministically with the fake model and at the agreed live-model threshold.
- No cross-user reference or tool access occurs.
- No agent path can call direct purchase completion.
- Existing commerce success/error paths remain covered.
- The legacy orchestration is removed only after cohort metrics show the agent path is at least as reliable for search/cart/checkout.

## North-star acceptance scenarios

### Scenario A — Broad clothing request

User: `I wanna buy new clothes.`

Expected behavior:

- The agent interprets a shopping goal, not checkout execution.
- It loads relevant preferences and wardrobe context.
- It asks one high-value question only if needed, such as occasion or full outfit versus one item; otherwise it states assumptions and searches.
- It shows a curated starting set and discusses refinements.
- `Add the second one` updates the exact selected offer.

Failure condition: empty checkout, exhaustive filter interrogation, or a new unrelated search for the follow-up.

### Scenario B — Similar owned shoes

User: `Find shoes similar to the ones I already have.`

Expected behavior:

- The agent searches confirmed inventory records.
- If several pairs exist, it shows a compact choice.
- It rehydrates the selected inventory product or constructs a typed fallback anchor.
- It searches/ranks similar products with the stored size and options.
- It explains why the top choices are similar.
- `Buy the first one in the same size` adds the exact available offer to the cart.

Failure condition: title-only guessing, lost size, nonexistent inventory grounding, or invented offer identity.

### Scenario C — Summer picnic mission

User: `Prepare everything I need for a summer picnic in San Francisco.`

Expected behavior:

- The agent creates a mission and checklist.
- It asks for party size/dietary restrictions only if needed or states conservative assumptions.
- It checks inventory for already-owned relevant items.
- It executes multiple searches and constructs a bundle covering the checklist.
- It partitions selections into merchant carts.
- It prepares checkout and shows one ECP action per merchant.

Failure condition: one literal search for the whole sentence, twenty unrelated items, incomplete checklist coverage, or a mixed-merchant cart.

### Scenario D — Inventory attribution

1. Prepare a Shopify embedded checkout.
2. Do not open the merchant ECP surface: inventory remains unchanged.
3. Open it and wait for `checkout:start`: inventory contains the server-side cart items immediately.
4. Close without completing payment: inventory remains, by explicit product decision.
5. Reopen/refetch/remount: quantities remain unchanged.

### Scenario E — Existing product UI and actions

1. Ask the agent to find shoes: results render through the current product batch and product cards with the current design.
2. Click Similar on the third card: anchored similar results render through the same current product components.
3. Click Pin: persistent pin state updates immediately and remains after reload.
4. Select the first two products and click Compare: the current comparison UI opens.
5. In a fresh result set, type `compare the first two`: the agent resolves those product references and opens the same comparison UI.
6. Exercise Add to cart, Watch, Reviews, Find a code, and any other visible CTA: each performs real behavior and retains its current component and placement.

## Test strategy

### Unit and component tests

- Tool schema generation and validation.
- Policy and allowlist selection.
- Context budgets and artifact references.
- Run state transitions, cancellation, limits, repeated-call detection, and safe failures.
- Shopping mission coverage and merchant partitioning.
- Frontend event reducer, cursor replay, reconnect, and each typed renderer.
- Product-result component regression coverage proving agent artifacts preserve the current card layout and every CTA.
- Direct-click tests proving CTA handlers do not invoke the model.

### Backend integration tests

- Liquibase and JPA constraints.
- Authenticated conversation/run ownership.
- Reconnectable SSE replay.
- Scripted model -> tools -> final response.
- Exact-offer cart mutation through existing services.
- Parity tests proving a direct CTA and its equivalent agent tool produce the same domain state and typed artifact.
- Multi-merchant partial failure.
- Checkout preparation through the existing ECP bootstrap.
- Inventory attribution idempotency and identity preservation.

### Scenario evaluations

- Keep prompts and expected invariants as versioned test fixtures.
- Assert required tool usage and domain outcomes, not exact assistant prose.
- Use a fake model for deterministic CI.
- Run live model evaluations manually or in a protected scheduled environment with fixed budgets.
- Do not use real purchases in automated evaluation.

## Delivery and migration rules

- Every ticket leaves the existing product deployable.
- Phase 1 lands before the agent can rely on inventory.
- Phase 2 runs behind a feature flag until complete.
- Existing endpoints and `OpenRouterChatClient` continue serving legacy flows during migration.
- New agent tools call public module services, not controllers and not repositories.
- Provider-specific code remains behind provider-neutral module boundaries.
- All controller request/response records use explicit OpenAPI `@Schema` required modes.
- All service commands/queries use Jakarta validation at the service boundary.
- Database changes use Liquibase XML and `text` for strings.
- Remote I/O is never wrapped in a long database transaction.
- Persist domain identifiers and bounded typed facts, not complete raw provider payloads.
- Never log model prompts containing sensitive user context, tool arguments/results, addresses, tokens, checkout URLs, or credentials.
- User-visible progress describes real activity without exposing chain-of-thought.

## Explicitly out of scope for this milestone

- Direct agent-controlled payment or `complete_checkout`.
- Standing spending mandates or autonomous charging.
- Providers beyond the existing Shopify path.
- Multi-agent/supervisor frameworks.
- General web browsing as a commerce source.
- Vector database adoption solely for chat memory.
- Semantic or visual embeddings unless inventory/canonical rehydration plus provider similarity proves insufficient.
- Rewriting catalog, cart, checkout, UCP, or Shopify functionality that already meets its contracts.
- Treating an external fallback link click as a purchase; only confirmed ECP start receives the early inventory rule in v1.

## Definition of done

The milestone is complete when:

- Phase 1 inventory behavior and idempotency pass all tests.
- All north-star scenarios work end to end.
- Agent-found products preserve the current product-card design, components, CTA layout, and click behavior.
- All visible product CTAs perform real actions, and their natural-language equivalents use the same application capabilities.
- The model selects and iterates over real Meant tools.
- Conversations, runs, tool calls, results, and events are server-owned and recoverable.
- Natural-language decisions no longer live in frontend regexes.
- Exact offers are used for every cart mutation.
- The agent prepares carts and checkout but cannot complete payment.
- The existing ECP UI is the final user handoff.
- Shopify remains the only required provider, with no Shopify imports in `module.agent`.
- The legacy qualification-driven Discover orchestration can be disabled without losing search, cart, checkout, inventory, or history functionality.

## Planned implementation order

1. AGENT-001 — Stable checkout-attempt identity.
2. AGENT-002 — Idempotent checkout-to-inventory attribution ledger.
3. AGENT-003 — Confirmed ECP-start trigger.
4. AGENT-004 — Preserve inventory commerce identity and selected options.
5. AGENT-101 — Spring AI/OpenRouter gateway and fake-model harness.
6. AGENT-102 — Durable conversations, runs, tools, events, and missions.
7. AGENT-103 — Controlled run loop and policy.
8. AGENT-109 early slice — Feature-flagged agent shell, API client, event reducer, conversation tabs, interruption, and Stop.
9. AGENT-104 — Context, inventory, orders, references, and matching frontend artifacts.
10. AGENT-105 — Catalog, detail, similarity, comparison, and matching frontend artifacts.
11. AGENT-106 — Shopping missions, multi-item planning, and mission renderer.
12. AGENT-107 — Exact-offer cart tools, multi-merchant coordination, and cart renderer.
13. AGENT-108 — Checkout preparation, sequential ECP handoff, and checkout renderer.
14. AGENT-109 final slice — Remove Discover intent/search orchestration from the enabled agent path.
15. AGENT-110 — Evaluations, rollout, and legacy retirement.
