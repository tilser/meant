# Phase 6b — Native Checkout Completion + Signing + AP2

Ticket: MEA-56. Branch: claude/inspiring-poincare-1009d8.
Builds on 6a (MEA-55, merged): checkout create/get/update + handoff already exist.

> This is the money + crypto phase. Highest blast radius in the whole project. It gets
> its own threat model (below) and its own /plan-eng-review BEFORE implementation.

## Goal

Add native agentic checkout completion: `complete_checkout` + `cancel_checkout`, with the
two signing layers UCP requires, AP2 mandates, idempotency, and payment handlers — behind
a feature flag with canary rollout.

## Hard reality (from UCP 2026-04-08 spec + AP2 mandates spec, fetched live)

complete_checkout is NOT one signature. It is two layers:

1. **Transport: RFC 9421 HTTP Message Signatures** — `Signature-Input`, `Signature`,
   `Content-Digest` (SHA-256 of body) headers. Permissionless: merchant verifies us by
   our advertised public key (from our agent profile `signing_keys`). Plus required
   headers: `Idempotency-Key`, `Request-Id`, `UCP-Agent`, `Content-Type: application/json`.

2. **Application: AP2 mandate** (only when `dev.ucp.shopping.ap2_mandate` negotiated):
   - Merchant embeds `ap2.merchant_authorization` in the checkout response — JWS Detached
     (RFC 7515 App F), signed over the checkout body MINUS the `ap2` field, JCS-canonicalized
     (RFC 8785).
   - We must produce `ap2.checkout_mandate` — an **SD-JWT+kb** (Selective Disclosure JWT
     with Key Binding) that wraps the FULL checkout response INCLUDING the merchant's
     `ap2.merchant_authorization` (nested binding) + spend scope + `exp`.
   - We send `ap2.checkout_mandate` in the complete_checkout body; the payment mandate
     travels inside `payment.instruments[*].credential.token`.
   - **Security Lock:** once AP2 is negotiated, the session cannot fall back to handoff.
     Merchant rejects completion without the mandate (`mandate_required`).

3. **Errors are HTTP 200 + messages[]** with `recoverable` / `unrecoverable` severity, not
   HTTP error codes. Protocol errors (401/403/429/503) are separate.

4. **Status lifecycle:** `incomplete` -> `processing` -> `completed` | `canceled`. SCA /
   buyer review returns a `continue_url` to escalate to the browser.

## Threat model (STRIDE-lite, money + crypto)

| Threat | Vector | Mitigation |
|---|---|---|
| **Double charge** | retry / network timeout re-sends complete | `Idempotency-Key` per logical completion, persisted; same key on every retry of the same intent. Never auto-retry without the same key. |
| **Replay** | attacker re-sends a captured signed complete | RFC 9421 `created`/`nonce` + short signature TTL; merchant-side dedupe via Idempotency-Key. |
| **Over-spend** | agent completes beyond user's authorization | AP2 checkout_mandate binds an explicit spend scope + checkout totals; we verify totals match before signing the mandate. |
| **Key compromise** | signing key leaks | Keys in a KMS/secret store, never in DB/repo/logs. Key rotation via `kid`. Short-lived if possible. |
| **Wrong-cart / terms tamper** | merchant or MITM alters totals after our signature | Mandate covers full checkout incl. merchant signature (nested binding); we reconcile checkout id + totals + line items before completing. |
| **Clock skew** | signature `created`/`exp` rejected | Bounded skew tolerance; sync clock; surface clear error not silent fail. |
| **Mandate forgery** | forged checkout_mandate | We are the signer; private key in KMS. Merchant verifies our advertised public key. |
| **Partial completion** | complete succeeds remote, local order write fails | Order reconciliation via get_order + webhook (Phase 7); idempotency means a safe re-query, not a re-charge. |
| **Secret leakage** | tokens/mandates in logs | Never log payment tokens, mandates, signatures, or Content-Digest of bodies with PII. |

## Architecture decisions (from /plan-eng-review)

- **D2 — App-managed signing keys** (NOT KMS, for pre-launch). Private key loaded from a
  secret store (Vault/Secrets Manager) into the app. Trade-off accepted: key lives in heap.
  Hard requirements that come WITH this choice:
  - Key material held in `byte[]`/`char[]` that can be zeroed, NEVER `String` (String
    lingers in heap until GC).
  - Key never reaches logs/dumps (secret-hygiene test enforces this).
  - Fast rotate + revoke path via `kid` in the agent profile — this is the only defense
    if the key leaks (vs KMS "can't exfiltrate"). Publishing a new public key must be quick.
  - `SigningKeyProvider` abstracts the backend so migrating to KMS later is cheap if real
    money volume arrives.
- **D3 — Established crypto libraries** (NOT hand-rolled). nimbus-jose-jwt for
  JWS/SD-JWT/ES256, an existing RFC 8785 JCS library, BouncyCastle where needed. RFC 9421
  may need a thin own layer, but ONLY over a vetted signing primitive — never our own
  ES256/SD-JWT. [Layer 2]
- **D4 — Global feature flag** (default OFF = 6a handoff). Acceptable pre-launch because
  native checkout is off by default and the fallback is the working 6a handoff, so the
  flag IS the kill switch (flip off -> everyone on handoff). Trade-off vs per-merchant:
  no "disable just the broken merchant" granularity; revisit per-merchant when real
  volume arrives (cheap behind the same flag abstraction). Canary watch: completion rate,
  errors, charge mismatch.
- **D1 — Split into 4 tickets** by build order (6b-1 signing primitives, 6b-2 AP2 mandate
  + idempotency + consent, 6b-3 payment handlers, 6b-4 complete/cancel + flag + canary).
  Crypto is tested in isolation with known-answer vectors before it touches money.

## Capabilities / components

```
plugin/checkout/
  complete/  CompleteCheckoutCapability + dto/
  cancel/    CancelCheckoutCapability + dto/
  common/    (extends existing 6a common)

plugin/payment/                    NEW capability family
  shoppay/   ShopPayHandler + dto/
  googlepay/ GooglePayHandler + dto/
  card/      CardHandler + dto/
  common/    PaymentHandler SPI, registry

plugin/signing/                    NEW — the crypto core
  Rfc9421Signer        (Signature-Input / Signature / Content-Digest)
  Jcs                  (RFC 8785 canonicalization)  [Layer 2: use a library]
  Ap2MandateService    (SD-JWT+kb build, nested checkout binding)
  SigningKeyProvider   (KMS-backed, kid, rotation)  [Layer 1: KMS, not DB]

plugin/checkout/common/
  IdempotencyKeyStore  (persisted, per logical completion)
  buyer_consent capability (consent artifact before completion)
```

## Code quality decision

- **D5 — Single `CheckoutTotalsReconciler`** (NOT inline x3). Over-spend / wrong-cart
  defense (verify checkout id + totals + line items against the expected state) is needed
  in three places: before signing the AP2 mandate, during terms reconciliation, and when
  handling the complete response. One shared component, called from all three, so
  money-safety has one audited source of truth and one test. (DRY — flag repetition
  aggressively.)

## Flow

```
user confirms purchase (explicit consent)
   |
get_checkout (refresh) -> verify totals/line items match what user saw
   |
[AP2 negotiated?] --yes--> build ap2.checkout_mandate (SD-JWT+kb) over full
   |                       checkout incl. merchant_authorization + spend scope + exp
   |
build complete_checkout body { payment.instruments[...], ap2.checkout_mandate?, signals }
   |
JCS-canonicalize -> Content-Digest -> RFC 9421 sign (Signature-Input/Signature)
   |  + Idempotency-Key (persisted), Request-Id, UCP-Agent
   v
POST complete_checkout
   |
HTTP 200 + status:
   completed -> persist order ref (get_order / webhook reconciles in Phase 7)
   processing -> poll get_checkout / await webhook
   continue_url present (SCA) -> escalate to browser handoff
   messages[] unrecoverable -> surface clean error, do NOT retry
   messages[] recoverable -> retry with SAME Idempotency-Key
```

## Build order (derisk: crypto primitives first, money last)

1. **Signing primitives** (no money): `Jcs`, `Rfc9421Signer`, `SigningKeyProvider` (KMS),
   with RFC test vectors. Unit-tested in isolation against known-answer vectors.
2. **Agent profile `signing_keys`** — publish our public keys so merchants can verify.
3. **Idempotency store** — persisted, keyed per logical completion.
4. **AP2 mandate** (`Ap2MandateService`) — SD-JWT+kb build, nested binding, with vectors.
5. **buyer_consent** capability — consent artifact gates completion.
6. **Payment handlers** — Shop Pay / Google Pay / card, behind PaymentHandler SPI.
7. **complete_checkout / cancel_checkout** capabilities, wiring 1-6 together.
8. **Feature flag + canary** — off by default, enable per-merchant, watch.

## Test strategy decision

- **D6 — Known-answer vectors + round-trip** for all crypto (NOT round-trip alone).
  Compare JCS / RFC 9421 signature / AP2 mandate output against exact reference values
  from RFC 8785, RFC 9421, and the AP2 spec / reference implementations, PLUS round-trip
  verification. Round-trip alone passes even with a symmetric bug on both sides — but a
  merchant running a correct implementation would then reject our signature. Known-answer
  catches it by comparing against external truth. This is the interop guarantee.

## Coverage map (all greenfield — crypto + money, must be ★★★ on critical paths)

```
CRITICAL (money/security — must be ★★★):
  - IdempotencyKeyStore: same key on retry -> NO double-charge        [CRITICAL]
  - CheckoutTotalsReconciler: totals mismatch -> reject (over-spend)  [CRITICAL]
  - Security Lock: AP2 negotiated -> no handoff fallback              [CRITICAL]
  - Secret hygiene: tokens/mandates/sigs/keys never logged            [CRITICAL]
signing/: Jcs (known-answer: order/unicode/whitespace), Rfc9421Signer (known-answer
  vectors, Content-Digest, clock-skew bounds), Ap2MandateService (SD-JWT+kb round-trip,
  nested binding incl merchant_authorization, exp/spend-scope), SigningKeyProvider
  (kid rotation, revoke).
payment/: each handler build credential + parse result.
complete/cancel: completed -> order ref, processing -> poll, continue_url(SCA) ->
  escalate, recoverable -> retry SAME key, unrecoverable -> surface no retry.
E2E: consent -> sign -> complete happy; retry -> no double-charge.
```

## Tests (money correctness is non-negotiable)

- JCS: RFC 8785 known-answer vectors (ordering, unicode, whitespace).
- RFC 9421: signing known-answer vectors; Content-Digest correctness; clock-skew bounds.
- AP2: mandate build round-trips; nested binding includes merchant_authorization; exp.
- Idempotency: same key on retry does NOT double-charge; distinct intents get distinct keys.
- complete_checkout: completed / processing / continue_url(SCA) / recoverable /
  unrecoverable message paths.
- Security Lock: AP2 negotiated -> no handoff fallback.
- Secret hygiene: assert tokens/mandates/signatures never reach logs.
- E2E: consent -> sign -> complete happy path; retry-no-double-charge.

## Outside voice (Codex) — crypto/money hardening

Direction changes (cross-model, user-approved):
- **D7 supersedes D4 — per-merchant flag, NOT global.** Native checkout enable/disable per
  merchant (default OFF = handoff). One broken merchant disables only itself; the rest keep
  native. Money rollout needs granular disable even pre-launch.
- **D8 — Status-first reconciliation before any retry.** On timeout / 5xx / connection
  reset / unknown outcome, FIRST query status (get_checkout / get_order for the same logical
  completion); only then decide done vs retry. NEVER blindly re-POST complete — a lost
  response on a succeeded charge would double-charge. Idempotency is the backstop, not the
  first defense. "recoverable -> retry same key" is REPLACED by this.
- **D9 — Money is minor-unit `Long` (like UcpMoney) or decimal string end-to-end, never
  `Double`.** Floats + JCS number canonicalization = latent over/under-charge. Applies to
  `CheckoutTotalsReconciler`, AP2 mandate, and the JCS payload. Currency match mandatory.

Codex gaps folded in (no decision — straight hardening requirements):

RFC 9421 signing:
- Pin the exact covered components: `@method`, `@authority`, `@path`, `@query`,
  `Content-Type`, `Content-Digest`, `Idempotency-Key`, `Request-Id`. If `Idempotency-Key`
  isn't signed, an intermediary can alter it and defeat double-charge protection.
- `Content-Digest` must cover the ACTUAL UTF-8 wire bytes — the JCS-canonical bytes MUST be
  the bytes sent. Do not canonicalize-then-reserialize-differently.
- Replay: client `nonce` only helps if the merchant tracks it; don't assume merchant
  behavior. Bound `created` TTL; reject future/stale `created`; short signature lifetime.
- Algorithm confusion defense: pin allowed algs by context. No `none`, no RSA/EC
  substitution, never trust a JWK-provided `alg`, never cross-use transport keys for AP2
  verification.

AP2 / SD-JWT:
- VERIFY the merchant's `ap2.merchant_authorization` BEFORE building our mandate: detached
  JWS over checkout-minus-`ap2`, check `kid`/`alg`/issuer/merchant identity/`exp`, and that
  the verified payload matches the checkout being completed.
- Define exact "minus `ap2`" semantics: top-level `ap2` only, behavior for nested/unknown
  fields — ambiguous exclusion is a canonicalization attack surface.
- SD-JWT+kb done properly: holder key, `cnf`, KB-JWT proof, audience, nonce/challenge,
  `iat`, `exp`, replay handling. Signing an SD-JWT by us is NOT "+kb".
- Separate key purposes / `kid`s for transport signing vs AP2 issuer vs SD-JWT holder.
  Do NOT reuse one ES256 key everywhere (key-use confusion, broader compromise).
- Key rotation overlap: old public keys stay advertised during a grace period (merchants
  cache profiles); explicit revocation semantics.

State machine / idempotency (the double-charge surface):
- Local state machine lock: DB unique constraint / compare-and-set transition
  `authorized_to_complete -> completion_in_flight` so two app instances can't race the same
  checkout completion.
- Idempotency store persists: body hash, checkout id, consent id, amount, currency, merchant
  id, status, remote response, final order ref. Same key + different body = HARD FAIL.
- `cancel_checkout` vs `complete_checkout` race: cancel blocked once completion in-flight (or
  shares the same state machine + remote status reconciliation), so a cancel can't land after
  a successful charge.
- Partial completion reconciliation is needed in 6b (not deferred to Phase 7): after a
  timeout, query status before any retry decision (this is D8).

Consent / over-spend (broaden beyond totals):
- Buyer consent binds: user, merchant, checkout id, line items, total, currency, taxes,
  shipping address + method, payment instrument, timestamp, expiration, presented-terms hash.
  "User confirms purchase" is not auditable enough.
- `CheckoutTotalsReconciler` checks: totals, line items, shipping address, shipping method,
  tax, discounts, subscription/recurring + trial terms, tips, currency, merchant identity,
  and max-authorized amount. Decide spend-ceiling semantics: bind to "<= authorized max"
  (with explicit comparison rules), test it. Currency mismatch is a classic failure.

Payment handlers (not a generic builder SPI):
- Real tokens have cryptogram freshness, merchant binding, domain binding, PSP amount/currency
  binding, SCA liability behavior. Model these, don't treat as a plain build/parse SPI.

SCA / escalation:
- If AP2 forbids handoff fallback, browser SCA escalation MUST preserve AP2/session binding
  and must not silently degrade to ordinary merchant checkout.
- Error model is table-driven, not optimistic: HTTP 200 + unrecoverable business failure AND
  HTTP 409/422-style protocol mismatch both occur; handle both explicitly.

Operational secret hygiene (app-managed keys, beyond the log test):
- Disable heap dumps on OOM, scrub crash reports, restrict actuator/env endpoints, prevent
  debug logging of bodies, document incident rotation. The log test can't prove keys never
  reach a heap dump — these operational controls do.
- Telemetry policy: don't put checkout ids, idempotency keys, request ids, digests, token
  hashes, or mandate hashes into high-cardinality external telemetry.

Dependencies (resolve BEFORE implementation starts):
- Pin the RFC 8785 JCS library and the RFC 9421 approach (lib or thin layer): maintenance
  status, supported algorithms, available test vectors. Don't start 6b-1 with these open.

Negative interop test matrix (must exist): altered covered header, reordered query params,
duplicate headers, whitespace/body byte change, changed idempotency key, stale `created`,
future `created`, reused nonce, wrong `kid`, wrong `alg`, wrong merchant key, same
idempotency key + different body.

## Performance note (Section 4 — no blocking findings)

Signing is low-frequency (one per purchase, not per search), so ES256 + SD-JWT build
latency on the complete_checkout path is fine. One implementation detail: load the signing
key from the secret store ONCE (cached, with the rotation/revoke path able to refresh it),
not on every sign — avoids needless secret-store load and latency per completion.

## NOT in scope
- Multi-PSP routing beyond the three handlers.
- Refunds / post-purchase (Phase 7 orders territory).
- Non-AP2 payment-token-exchange variants (only AP2 mandate path here).

## Crypto libraries (PINNED — MEA-60 prerequisite, resolved)

One ES256 signing core, used across all four layers. Three of four are vetted libraries with
RFC test vectors — we wire crypto, we don't write it.

| Layer | Purpose | Library | Coordinates |
|---|---|---|---|
| Signing core | ES256 / JWS / JWK | nimbus-jose-jwt | `com.nimbusds:nimbus-jose-jwt:10.9.1` |
| JCS (RFC 8785) | canonical bytes so signatures match | erdtman java-json-canonicalization | `io.github.erdtman:java-json-canonicalization` (reference impl, Ryu numbers) |
| RFC 9421 | "I am Meant + request unaltered" (transport) | authlete http-message-signatures | `com.authlete:http-message-signatures` (has RFC9421Test.java with RFC-appendix vectors) |
| SD-JWT+kb (AP2) | "user authorized this spend" (business) | authlete sd-jwt | `com.authlete:sd-jwt:1.9` (RFC 9901, key binding) |

Notes:
- The Authlete libraries build on nimbus, so the whole stack shares one crypto core — no
  competing stacks.
- Authlete = OpenID-certified identity vendor; both libs Apache-2.0.
- Pin exact `http-message-signatures` version from its CHANGES.md at implementation time.
- JCS pitfall to enforce: the JCS canonical bytes MUST be the exact bytes Content-Digest
  covers and that go on the wire — do NOT canonicalize then re-serialize via Jackson
  differently (Codex's wire-bytes warning).

## 4-ticket split (D1) — to create after this review

- **6b-1 Signing primitives** (no money): Jcs (RFC 8785 lib), Rfc9421Signer (covered
  components pinned), SigningKeyProvider (app-managed, secret store, separate key purposes,
  rotation/revoke + grace period), publish `signing_keys` in agent profile. Known-answer
  vectors + round-trip. Pin crypto libs BEFORE starting.
- **6b-2 AP2 mandate + idempotency + consent**: verify merchant_authorization first;
  SD-JWT+kb (cnf/KB-JWT/audience/nonce/iat/exp); IdempotencyKeyStore + DB state-machine lock
  (compare-and-set); CheckoutTotalsReconciler (full field set, minor-unit Long); buyer_consent
  artifact.
- **6b-3 Payment handlers**: Shop Pay / Google Pay / card with cryptogram/merchant/domain/
  PSP binding (not a plain SPI).
- **6b-4 complete/cancel + rollout**: complete_checkout (status-first reconciliation, D8),
  cancel_checkout (race rules vs complete), per-merchant flag (D7, default OFF), canary,
  table-driven error model, SCA escalation preserving AP2 binding.

Each ticket: known-answer + round-trip crypto tests, negative interop matrix, secret-hygiene
+ operational controls, no `Double` on any money path.

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | not run |
| Codex Review | `/codex review` | Independent 2nd opinion | 1 | issues_found | 27 raised; 3 cross-model decisions + ~20 crypto/money gaps folded in |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | 9 issues, 0 critical gaps, full STRIDE threat model |
| Design Review | `/plan-design-review` | UI/UX gaps | 0 | — | not run (backend crypto; SCA escalation is minor FE) |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | not run |

- **CODEX:** outstanding crypto/money review — caught signed-component pinning, status-first
  reconciliation (double-charge), no-Double money, key-purpose separation, SD-JWT+kb rigor,
  state-machine lock, consent/over-spend field breadth, operational secret hygiene.
- **CROSS-MODEL:** 3 decisions where Codex overrode the review — D4→per-merchant flag (D7),
  retry→status-first (D8), money→no-Double (D9). All user-approved. Strong consensus on
  threat model.
- **UNRESOLVED:** 0.
- **VERDICT:** ENG CLEARED — ready to split into 4 tickets (6b-1..6b-4) and implement
  6b-1 first. 6b-1 must pin crypto libraries before any code. Highest blast radius in the
  project — per-merchant flag default OFF, canary, status-first reconciliation.
