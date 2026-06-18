# MEA-35 — Spike: UCP embedded transport & order-session integration feasibility

**Status:** Findings & recommendation (no code shipped — this is a timeboxed investigation)
**Author:** David Tilšer
**Date:** 2026-06-18
**De-risks:** [MEA-33 — Express checkout via UCP payment handlers](https://linear.app/machinecommerce/issue/MEA-33), [MEA-34 — Post-purchase order epic](https://linear.app/machinecommerce/issue/MEA-34) / [MEA-36 — Real order tracking](https://linear.app/machinecommerce/issue/MEA-36), [MEA-41 — AP2 mandates](https://linear.app/machinecommerce/issue/MEA-41)

---

## TL;DR

The "accuracy notes" in the parent tickets are correct: **checkout, order, and fulfillment are NOT MCP tools.** The UCP MCP endpoint only exposes the 5 storefront tools (`search` / `details` / cart × 2 / policies). Checkout/order/fulfillment are separate **capabilities**, each offered over its own set of **transport bindings** (REST, MCP, A2A, Embedded). They share one logical operation set — `Create / Get / Update / Complete / Cancel` for checkout, `Get Order` + webhooks for order.

**The single most important finding:** Meant **already ingests and persists everything needed to discover and route these flows** — it just never *drives* them. `merchant_service` rows already store the `embedded` / `rest` transport endpoints; `merchant_capability` rows already store the checkout/order/fulfillment spec+schema URLs; `merchant_payment_handler` rows already store handler id/version/spec/schema. The gap is purely on the *client/driver* side, not on discovery/ingestion.

**Recommended build approach** — do them in this order, because each unblocks the next and the risk is back-loaded:

1. **Order tracking first (MEA-36).** Lowest risk, highest trust payoff, server-to-server only. Uses the **REST** binding of the order capability (`GET /orders/{id}`) + a webhook receiver. No browser, no payment, no AP2. **Est: M (1–1.5 wk).**
2. **Checkout-over-REST + `continue_url` fallback (subset of MEA-33).** Drive `Create/Update/Get Checkout` over REST to compute real totals/fulfillment in-app, then hand off to the merchant's `continue_url` for payment. Still no embedded iframe, no payment handler. **Est: M (1.5 wk).**
3. **Embedded express checkout (full MEA-33).** Requires a host-side **ECP (Embedded Checkout Protocol)** JSON-RPC message loop in a webview/iframe, payment-handler invocation (Shop Pay / Google Pay), and OAuth identity linking. This is the real integration surface and the bulk of the risk. **Est: L (3–4 wk) + a hard external dependency on merchant onboarding.**
4. **AP2 mandates (MEA-41).** Layered on top of #3 once a manual embedded purchase works end-to-end. Cryptographic signing (JWT + SD-JWT-VC), `CheckoutMandate` + `PaymentMandate`, submitted to the merchant `/complete` endpoint. **Est: L (2–3 wk), spike-of-its-own recommended before committing.**

**Biggest risk is not code — it's merchant onboarding & credentials.** Every flow beyond anonymous storefront search needs OAuth 2.0 identity linking (Authorization Code + PKCE, `private_key_jwt`/`tls_client_auth`), and webhooks + embedded checkout require **per-platform partner onboarding** with each merchant/Shopify. We cannot self-serve our way to a live express-checkout demo without that. This should be validated with one design-partner merchant before MEA-33 is estimated as committed work.

---

## Method

Grounded the investigation in two sources:

1. **The live UCP spec** (`ucp.dev/latest/specification/...`) — embedded-checkout, checkout, order, identity-linking, and the UCP+AP2 documentation.
2. **The current Meant codebase** — what is already ingested vs. what is actually driven.

Spec sections referenced are listed under [Sources](#sources).

---

## What Meant has today (codebase reality)

| Surface | State today | Evidence |
|---|---|---|
| Storefront tools (search/details/cart/policies) | **Driven** over MCP | `MerchantMcpToolClient`, `CartClient` (`get_cart`/`update_cart`) |
| Checkout | **Redirect only** — returns merchant `checkoutUrl`, frontend "Opening checkout…" opens merchant site | `CartService.checkout()` → `CheckoutResult(checkoutUrl)`; `MeantApp.tsx` `Check out at {merchant}` |
| Orders | **Mock** — `DEFAULT_ORDERS` | `frontend/src/features/meant/data.ts`, `MeantApp.tsx` |
| Embedded / REST transport endpoints | **Ingested & persisted, never used** | `merchant_service` (`transport`, `endpoint`), populated in `MerchantEnrichmentPersistenceService.saveServices()` |
| Checkout/order/fulfillment capabilities | **Ingested & persisted, never used** | `merchant_capability` (+ `extends`, `requires`), `saveCapabilities()` |
| Payment handlers | **Ingested & persisted, never used** | `merchant_payment_handler` (id/version/spec/schema), `savePaymentHandlers()` |
| `has_order` / `has_checkout` / `has_payment_token` flags | **Ingested, never used** | `MerchantRaw`, set in `UcpMerchantImportService.toMerchantRaw()` |

**Implication:** discovery and ingestion are done. The profile parser (`UcpProfile`, `UcpServiceDefinition`) already handles `transport: "embedded"`, `transport: "rest"`, capability `extends`/`requires`, and `payment_handlers` (see `UcpProfileParsingTest`). New work is a set of **capability driver/clients**, not new ingestion.

---

## Answers to the five spike questions

### 1. How is an order/checkout session created and driven over the embedded transport (vs. MCP)?

Checkout is a **capability** with five logical operations — `Create / Get / Update / Complete / Cancel` — exposed over **four transport bindings: REST, MCP, A2A, and Embedded**. The same operations map onto each binding.

- **Session creation:** `Create Checkout` takes `line_items`, `buyer`, `context`, `signals`, `attribution`, optional `payment`. The merchant returns a checkout object: `id`, `status`, `line_items`, `totals`, `messages`, and `continue_url`.
- **Embedded transport (ECP)** is **JSON-RPC over a host↔iframe/webview channel**, *not* a server API call. The host loads the merchant's checkout UI by augmenting `continue_url` with ECP query params (`ec_version` required, `ec_delegate`, `ec_color_scheme`, `ec_auth`). The embedded UI then drives a **bidirectional message loop**:
  - Handshake: `ec.ready` (EC→host request) → `ec.ready` (host→EC response, negotiates version, may pre-share payment instruments).
  - Lifecycle notifications: `ec.start`, then `ec.line_items.change`, `ec.buyer.change`, `ec.payment.change`, `ec.totals.change`, `ec.fulfillment.change`, `ec.messages.change`.
  - Terminal: `ec.complete` (carries the created `order` object + permalink).
- **Key architectural point:** ECP is a **front-end/host integration** (the host app embeds the merchant checkout and answers delegated requests), whereas REST/MCP are **server-to-server**. Meant can therefore choose:
  - **REST binding** for headless server-side checkout (compute totals/fulfillment in-app) → then hand off to `continue_url` for payment. Lower risk, no browser loop.
  - **Embedded binding** for true in-context express pay → required for the "don't leave Meant" UX in MEA-33, but needs the full ECP message loop in the client.

The version negotiated at `ec.ready` is **session-bound** and must not change mid-session.

### 2. What does the `order` capability return and how is it polled/subscribed?

`dev.ucp.shopping.order` returns a full **order state snapshot**:

- **Fulfillment status** per line item: `processing` / `partial` / `fulfilled` / `removed`, with original/total/fulfilled quantities.
- **Shipment tracking** via an **append-only Fulfillment Events log**: event types `processing`, `shipped`, `in_transit`, `delivered`, `failed_attempt`, `canceled`, `undeliverable`, `returned_to_sender`, with tracking number / URL / carrier.
- **Adjustments** (returns/refunds/credits/disputes/cancellations): types are open-ended; statuses `pending` / `completed` / `failed`; signed monetary amounts (negative = customer refund).

**Delivery model is webhook-push, not polling.** Merchants push lifecycle updates to a **platform-provided webhook URL** (set during partner onboarding). The platform "SHOULD rely on webhooks as the primary channel and use **Get Order** (`GET /orders/{id}`) for reconciliation." Webhooks are signed with **RFC 9421** HTTP Message Signatures — required headers `Signature-Input`, `Signature`, `Content-Digest`, `UCP-Agent`.

- **Returns initiation:** the order spec models returns only as *post-order adjustments* (representation), and does **not** specify a buyer-initiated "start a return" call. **Open question / risk** for MEA-36's "initiate a return" AC — likely merchant-/Shopify-specific or via `continue_url`. Needs confirmation per merchant.
- **Transport:** REST (`GET /orders/{id}` + webhook `POST`) and MCP. **REST is the pragmatic choice** for Meant's server-side order sync.

### 3. What's required to drive a payment handler (Shop Pay / Google Pay) end-to-end, incl. AP2 guardrails?

Payment handlers are invoked **through the embedded checkout's payment delegation**, not directly:

1. Host opts into `payment.credential` / `payment.instruments_change` via `ec_delegate`.
2. Buyer selects a method → EC fires `ec.payment.instruments_change_request` → host shows native picker → responds with selected instrument.
3. Buyer clicks "Place Order" → EC fires `ec.payment.credential_request` → **host invokes the payment handler** (Shop Pay / Google Pay) for user auth (biometric/PIN) + token generation → host responds with `{ credential: { type: "token", token: "…" } }`.
4. **Hard rule:** "Silent tokenization is strictly PROHIBITED when the trigger originates from the Embedded Checkout." User confirmation via native UI is **mandatory** — this directly shapes Meant's auto-buy UX ([MEA-2](https://linear.app/machinecommerce/issue/MEA-2)).

Handler discovery is already done (`merchant_payment_handler` from `checkout.ucp.payment_handlers` in `.well-known/ucp`). **AP2 (see Q on MEA-41 below) is the mechanism that lets an agent satisfy step 4 without a live human tap — by submitting a pre-signed mandate instead of an interactive credential.**

### 4. How does `fulfillment` config (multi-destination, method combinations) affect cart/checkout UX?

Fulfillment is **modelled as an optional UCP extension** (so digital goods need none). Structure: **Fulfillment Option** = `id`, `title`, `description`, `carrier`, `earliest_fulfillment_time`, `latest_fulfillment_time`, `totals[]`.

- **Multi-destination / per-item methods:** modelled by returning **multiple fulfillment option groups** within `line_items`/cart context — buyers can pick distinct delivery methods per item or per address.
- In the **embedded** flow, address changes are delegated: `ec.fulfillment.address_change_request` → host shows native address picker → responds with updated `fulfillment.methods[]` (each with `selected_destination_id` + `destinations[]`).
- **UX implication for Meant's single multi-merchant cart:** fulfillment is per-merchant-checkout, so the existing "checkout handled at each merchant" grouping in `MeantApp.tsx` is the right mental model. Multi-destination within one merchant is possible but adds selection UI; recommend **deferring multi-destination** for v1 and supporting a single shipping address per merchant checkout.

### 5. What auth/credentials/merchant onboarding is required beyond the storefront MCP?

This is the **gating dependency**. Beyond anonymous storefront search:

- **Identity Linking (OAuth 2.0)** for any user-authenticated operation (`dev.ucp.shopping.checkout:manage` scope, saved addresses, order history):
  - Authorization Code flow with **PKCE (S256)**; discover OAuth metadata via **RFC 8414**; return `iss` to prevent mix-up attacks; token revocation per **RFC 7009**.
  - Server-side platform auth **SHOULD prefer asymmetric** `private_key_jwt` or `tls_client_auth` (advertised via `token_endpoint_auth_methods_supported`).
  - User identity tokens go in `Authorization: Bearer …`; refresh via `refresh_token`.
- **Partner onboarding (per merchant / Shopify):** required to register Meant's **webhook URL** (orders) and to be an approved **host** for embedded checkout + payment handlers. This is **out-of-band** (partner portal / agreement), not self-serve from the profile.
- **Account-state signals:** merchants should support **OpenID RISC Profile 1.0** for async revocation events.

**Net:** there is no path to live express checkout or webhook-driven order tracking without OAuth identity linking + a partner onboarding relationship. This must be secured with at least one design-partner merchant.

---

## AP2 mandates (informs MEA-41)

AP2 is what makes **agentic auto-buy** legitimate. Two credentials are generated at completion:

- **CheckoutMandate** — JWT containing the **hash of the CheckoutObject** (the business signs checkout state and declares AP2 support).
- **PaymentMandate** — an **SD-JWT-VC** credential with payment authorization, **scoped to the checkout hash** (prevents token replay / amount manipulation).

Both are submitted together to the business's **`/complete`** endpoint. **Trust triangle:** business verifies the CheckoutMandate, payment processor independently verifies the PaymentMandate, platform only coordinates — **raw credentials never flow through Meant** (Verifiable Digital Credentials). This satisfies MEA-41's "raw credentials never pass through Meant" AC by construction.

**Risk:** SD-JWT-VC signing, key management, and the `/complete` submission are net-new crypto surface. Recommend a **dedicated AP2 spike** before committing MEA-41, gated on a working manual embedded purchase from #3.

---

## Recommended build approach, effort & risk

| # | Deliverable | Transport | Effort | Risk | Hard dependencies |
|---|---|---|---|---|---|
| 1 | Real order tracking & status (MEA-36) | Order **REST** `GET /orders/{id}` + webhook receiver (RFC 9421 verify) | **M** (1–1.5 wk) | Low | Partner onboarding to register webhook URL; returns-initiation per-merchant TBD |
| 2 | Server-side checkout + `continue_url` handoff (MEA-33 subset) | Checkout **REST** `Create/Update/Get` | **M** (1.5 wk) | Med | OAuth identity linking for `checkout:manage` |
| 3 | Embedded express checkout + payment handler (full MEA-33) | **Embedded (ECP)** JSON-RPC loop + Shop Pay/Google Pay | **L** (3–4 wk) | **High** | Host partner onboarding; webview/iframe message loop; mandatory user-confirmation UI |
| 4 | AP2 mandates (MEA-41) | JWT + SD-JWT-VC → merchant `/complete` | **L** (2–3 wk) | **High** | Working #3; key mgmt; own spike recommended |

**Sequencing rationale:** value and trust land early (orders), risk is back-loaded (embedded + crypto), and each step is independently shippable. Order tracking alone removes the single biggest credibility gap (the mock Orders screen) without touching payments.

### Concrete next code surfaces (when work starts — not this ticket)

- New `order` module: `OrderCapabilityClient` (REST `GET /orders/{id}`), webhook controller with RFC 9421 verification, persistence to replace `DEFAULT_ORDERS`. Reuse `MerchantService`/`MerchantCapability` lookups to find the order endpoint + confirm `has_order`.
- New `checkout` capability client mirroring `CartClient`, but routing by **capability transport** (prefer REST endpoint from `merchant_service`) instead of the hard-coded MCP path.
- Identity-linking OAuth client (Authorization Code + PKCE, `private_key_jwt`).

---

## Open questions to resolve before committing MEA-33/36/41

1. **Merchant onboarding:** which design-partner merchant (Pier1? a Shopify dev store?) will grant Meant host/partner status + webhook registration? Without this, embedded checkout and order webhooks cannot be demoed.
2. **Returns initiation:** the order spec only models returns as adjustments; how is a return *started*? Confirm per merchant (likely `continue_url` or Shopify-specific) before promising MEA-36's return AC.
3. **AP2 availability:** do target merchants actually advertise AP2 support today, or is MEA-41 blocked on merchant-side rollout?
4. **Embedded host environment:** Meant's client is React (`MeantApp.tsx`); confirm we can host an iframe/webview with the ECP postMessage loop in the target deployment (web vs. native shell).

---

## Sources

- Embedded checkout (ECP): https://ucp.dev/latest/specification/embedded-checkout/
- Checkout capability: https://ucp.dev/latest/specification/checkout/
- Order capability: https://ucp.dev/latest/specification/order/
- Identity linking: https://ucp.dev/latest/specification/identity-linking/
- UCP and AP2: https://ucp.dev/documentation/ucp-and-ap2/
- Overview: https://ucp.dev/latest/specification/overview/

Codebase evidence: `MerchantEnrichmentPersistenceService` (services/capabilities/payment handlers persisted), `UcpProfileParsingTest` (embedded/rest transports parsed), `CartService.checkout()` (current redirect behavior), `frontend/src/features/meant/data.ts` (`DEFAULT_ORDERS` mock).
