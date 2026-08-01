# Meant Mobile App Implementation Plan

Status: revised after repository validation
Last validated: 2026-08-01

Goal: add an Expo mobile app to this repository that reuses Meant's Spring Boot API, Supabase identity, durable commerce-agent protocol, and portable web domain logic. The first foundation work should not change backend behavior; later phases may add narrow mobile-specific contracts where the current browser contract is not portable.

## Validity Assessment

The original direction is still sound:

- keep mobile in this repository;
- use Expo, Expo Router, TypeScript, and Bun workspaces;
- share generated API contracts and pure TypeScript logic, not web UI;
- use Supabase sessions to obtain bearer tokens for the Spring API;
- use React Query for request state while keeping ordinary client state in React;
- defer push notifications and unnecessary native customization.

Several implementation assumptions were no longer valid and are corrected in this revision:

- Discover is now backed by the durable server-side commerce agent. Mobile must implement conversations, runs, event replay, reconnection, and typed artifacts before treating discovery as complete.
- `POST /api/v1/users/me/product-search-qualifications` no longer exists. Qualification still exists internally, but the agent owns it. Mobile must not recreate the retired client-side qualify-then-search orchestrator.
- The checked-in `frontend/src/api/schema.d.ts` does not yet contain the agent endpoints, even though the controllers are live. OpenAPI regeneration/coverage is therefore a prerequisite to calling the generated schema the shared source of truth.
- Checkout is capability-based. The backend can select embedded checkout, direct completion, or merchant handoff; direct completion is disabled by default and the agent does not complete payment. Mobile cannot promise that every checkout stays inside Meant.
- The current embedded-checkout bootstrap lifecycle is browser-specific and binds sessions to an allowed HTTP `Origin`. A native app needs an explicit mobile lifecycle contract or a documented alternative; it must not spoof a web origin.
- The repository has no account-deletion endpoint or deletion-request page. Those are release blockers for an app that supports account creation.
- Apple native sign-in is an iOS path. Google native sign-in requires a development build; “both providers natively on both platforms” is not an accurate requirement.

## Sources of Truth

This document owns mobile delivery sequencing. It does not redefine backend commerce behavior.

Use, in order:

1. Current backend controllers and their request/response records.
2. The current frontend behavior and tests for agent recovery, product mapping, cart reconciliation, and checkout policy.
3. `plans/commerce-agent-implementation-plan.md` for agent semantics.
4. `plans/personal-commerce-os-implementation-plan.md` for provider-neutral catalog, offer, cart, and checkout semantics.

If a planning document disagrees with running code, resolve and document the contract drift before copying it into mobile.

## Current Repository Baseline

As of the validation date:

- There is no root `package.json`, root Bun lockfile, `mobile/`, or `packages/` directory.
- `frontend/` is a Bun application with its own `bun.lock`; CI pins Bun 1.3.9 and Node 24.
- `frontend/src/lib/apiClient.ts` contains a mixture of generated OpenAPI types and handwritten wire types/helpers.
- `frontend/src/api/schema.d.ts` is generated from `/v3/api-docs`, but is currently stale with respect to the agent controllers.
- The commerce agent is enabled by default and the web Discover surface uses it.
- Agent conversations and events are durable on the server; disconnecting a stream does not cancel a run.
- The backend returns RFC 7807 `ProblemDetail` responses with a machine-readable `code`; the web client represents these as `ApiError(status, code)`.
- Checkout returns `nextAction`, `selectedRail`, capability decisions, and typed ineligibility reasons. Web checkout supports embedded Checkout Kit plus a merchant-handoff fallback.
- Supabase Storage buckets already exist for profile pictures and private inventory photos.

## Recommendation and Repository Shape

Keep the existing top-level application layout and add a light Bun workspace:

```text
backend/
frontend/
mobile/
packages/
  api-client/
  shared/
package.json
bun.lock
```

The root package should be private and own these workspaces:

```json
{
  "name": "meant",
  "private": true,
  "workspaces": ["frontend", "mobile", "packages/*"]
}
```

Do not add Nx or Turborepo initially. Expo supports standard Bun workspaces and automatic Metro configuration in current SDKs. Keep one root `bun.lock`; EAS chooses Bun when it sees the Bun lockfile. Pin all resolved versions in the committed lockfile even though scaffolding starts from the latest stable Expo SDK.

Do not force one React version across web and mobile. Each app must declare the React version supported by its framework/Expo SDK. Shared packages must avoid importing React Native, React DOM, or app-owned React instances unless they are intentionally platform-specific. Run `expo-doctor` after every Expo/native dependency change.

References:

- https://docs.expo.dev/guides/monorepos/
- https://docs.expo.dev/guides/using-bun/
- https://docs.expo.dev/build-reference/build-with-monorepos/

## Mobile Stack

Use:

- Expo managed workflow with Continuous Native Generation;
- the latest stable Expo SDK at scaffold time, then exact compatible versions in `package.json` and `bun.lock`;
- TypeScript and Expo Router;
- React Native primitives and mobile-owned UI;
- `@supabase/supabase-js` with a React Native storage adapter;
- the shared `openapi-fetch` client for ordinary JSON endpoints;
- `expo/fetch` for authenticated streamed responses;
- `@tanstack/react-query` for snapshot/request state;
- a pure reducer for durable agent events and transient streaming state;
- EAS development builds from the auth phase onward.

Avoid initially:

- custom native modules maintained by Meant;
- checked-in `ios/` and `android/` directories unless a dependency proves CNG insufficient;
- shared web/mobile UI components;
- an app-wide state framework;
- push notifications;
- a second JavaScript package manager.

## Shared Workspace Packages

### `packages/api-client`

Purpose: provide the platform-neutral backend contract, auth injection, error behavior, and JSON endpoint wrappers used by web and mobile.

Proposed shape:

```text
packages/api-client/
  src/
    schema.d.ts
    client.ts
    errors.ts
    agent.ts
    users.ts
    products.ts
    cart.ts
    orders.ts
  package.json
  tsconfig.json
```

The client must accept platform concerns as dependencies:

```ts
export interface MeantAuthSnapshot {
  accessToken: string | null
  userId: string | null
}

export interface MeantApiClientOptions {
  baseUrl: string
  getAuth: () => Promise<MeantAuthSnapshot>
  fetch?: typeof globalThis.fetch
}
```

Requirements:

- Preserve `ApiError.status` and `ApiError.code` exactly; never branch on human error text.
- Preserve account-bound request checks so a response started by one signed-in user cannot mutate state after the active account changes.
- Accept `AbortSignal` and do not automatically retry mutations.
- Use bounded retry rules for reads: do not retry ordinary 4xx responses; honor `429`/`Retry-After`; avoid amplifying expensive catalog and agent endpoints.
- Use `expo/fetch` for mobile streams. Do not require an `EventSource` implementation because authenticated streams need bearer headers and cursor recovery.
- Keep React Query hooks in each application. The package exports transport functions, not app lifecycle or UI state.

Before moving the schema:

1. Regenerate OpenAPI from the current backend.
2. Confirm that all agent conversation/run endpoints and request/response schemas are present.
3. Add a repeatable root generation command and a CI drift check, or explicitly document any deliberately handwritten preview protocol.
4. Migrate web imports incrementally; do not combine the mobile scaffold with a wholesale rewrite of `frontend/src/lib/apiClient.ts`.

### `packages/shared`

Purpose: pure TypeScript behavior that two active clients need.

High-value candidates now include:

- the versioned agent event parser/reducer, cursor deduplication, and recovery decisions;
- agent artifact-to-product/cart projections;
- money and currency-minor-unit helpers;
- product/offer mapping and variant selection;
- cart partitioning, counts, and stale-cart reconciliation;
- order mapping;
- preference/filter helpers.

Do not move browser storage, DOM components, CSS, browser Checkout Kit adapters, Supabase initialization, or React Native components. Extract a helper only when mobile is about to use it, retaining the web tests during the move.

## Proposed Mobile Structure

```text
mobile/
  app/
    _layout.tsx
    (auth)/
      sign-in.tsx
      callback.tsx
    (tabs)/
      _layout.tsx
      index.tsx
      saved.tsx
      inventory.tsx
      cart.tsx
      profile.tsx
    conversation/
      [conversationId].tsx
    product/
      [canonicalProductKey].tsx
    compare.tsx
    order/
      [orderId].tsx
    merchant-auth/
      callback.tsx
  src/
    api/
      client.ts
    auth/
      supabase.ts
      session.tsx
    agent/
      stream.ts
    features/
      discover/
      product/
      saved-products/
      inventory/
      cart/
      checkout/
      profile/
      orders/
    components/
    theme/
    lib/
  app.config.ts
  eas.json
  package.json
  tsconfig.json
```

Primary tabs remain Discover, Saved, Inventory, Cart, and Profile. Compare, orders, conversation history, product detail, preferences, and merchant authorization are nested flows. Preserve merchant boundaries in cart and checkout even when the UI presents a coordinated multi-merchant experience.

## Authentication Plan

Supabase remains the identity provider; Spring remains the resource server.

Implementation requirements:

- Configure `persistSession: true`, `autoRefreshToken: true`, and `detectSessionInUrl: false` on native.
- Follow Supabase's React Native lifecycle pattern: use `processLock` and start/stop token auto-refresh when `AppState` moves between foreground and background.
- Start with AsyncStorage, which is the documented Supabase React Native path. If the threat model requires Keychain/Keystore storage, implement and test a chunked SecureStore adapter and handle native size errors; do not assume an exact universal 2048-byte limit.
- Use `EXPO_PUBLIC_SUPABASE_URL` and prefer `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY`. A legacy anon key is also public, but if retained temporarily, name and migration behavior must be explicit across web and mobile.
- Never ship a service-role key, OpenRouter key, Shopify secret, EAS token, or backend credential in an `EXPO_PUBLIC_` value.
- Use `expo-apple-authentication` and `supabase.auth.signInWithIdToken({ provider: 'apple', ... })` on iOS. Apple auth can be tried in Expo Go, but production identity and capability testing must use the real development build and physical device.
- Use a supported Google native sign-in package and `signInWithIdToken({ provider: 'google', ... })`. The native Google module requires a development build and correct iOS URL scheme, Android application ID, and signing certificate fingerprints.
- Treat email/password parity as a product decision, not a hidden development fallback. If included, implement sign-up, confirmation, password reset, and deep-link recovery as a complete supported path.
- Configure a custom app scheme in Phase 1. Add verified universal/app links later when the production domain and association files exist.

The iOS bundle identifier and Android application ID are required before production provider configuration. Apple native sign-in needs the iOS app identity/capability; Google requires platform OAuth clients and Android signing fingerprints. These identifiers therefore block Phase 1 acceptance, not merely store submission.

References:

- https://supabase.com/docs/guides/auth/quickstarts/react-native
- https://supabase.com/docs/reference/javascript/auth-signinwithidtoken
- https://supabase.com/docs/guides/auth/social-login/auth-apple
- https://docs.expo.dev/versions/latest/sdk/apple-authentication/
- https://react-native-google-signin.github.io/docs/setting-up/expo

## Agent and Discovery Contract

The commerce agent is the primary Discover API. Mobile must not reproduce the removed client-owned qualification conversation.

Conversation lifecycle:

- `POST /api/v1/users/me/agent/conversations`
- `GET /api/v1/users/me/agent/conversations`
- `GET /api/v1/users/me/agent/conversations/{conversationId}`
- `PATCH /api/v1/users/me/agent/conversations/{conversationId}`
- `DELETE /api/v1/users/me/agent/conversations/{conversationId}`
- `POST /api/v1/users/me/agent/conversations/{conversationId}/turns`
- `POST /api/v1/users/me/agent/conversations/{conversationId}/actions`

Run lifecycle:

- `GET /api/v1/users/me/agent/runs/{runId}`
- `POST /api/v1/users/me/agent/runs/{runId}/cancel`
- `GET /api/v1/users/me/agent/runs/{runId}/events?afterCursor={cursor}`

Required client semantics:

- Submitting a turn returns `202 Accepted`; it does not return the final answer.
- Read the authenticated GET SSE stream with `expo/fetch` and the Supabase bearer token.
- Persist the latest applied cursor per active run; ignore duplicates and unknown future event types.
- On foreground/reconnect, replay after the cursor. If event history is no longer available, recover from the run and conversation snapshots.
- A network disconnect does not cancel the server run. Only the explicit cancel endpoint or replacement-turn behavior does.
- Keep event-stream state in a deterministic reducer; use React Query for conversation/run snapshots and invalidation around terminal events.
- Render products, carts, checkout, comparisons, and other commerce output from typed artifacts. Do not scrape assistant prose.
- Direct CTA clicks execute deterministic APIs and use `/actions` where required so the conversation observes the mutation without a model round trip.

`POST /api/v1/users/me/product-searches` and `POST /api/v1/users/me/product-searches:stream` remain catalog contracts that require a server-issued READY qualification. They are not the main mobile chat orchestration surface. Mobile should use them only for a deliberately designed non-agent fallback that has an actual way to obtain the required qualification.

The current Expo fetch API exposes streamed response bodies and `getReader()` on mobile. Verify event framing, abort, replay, background/foreground recovery, and long-running runs on physical iOS and Android devices.

Reference: https://docs.expo.dev/versions/latest/sdk/expo/#expofetch-api

## Other Backend Endpoint Groups

Mobile calls Meant's buyer-facing APIs, never plugin/UCP transport endpoints directly.

Profile and settings:

- `GET /api/users/me`
- `PATCH /api/users/me`
- `GET /api/users/me/settings`
- `PATCH /api/users/me/settings`
- `DELETE /api/users/me/settings/product-search-preferences/{scope}`
- `PATCH /api/users/me/newsletter`
- `PATCH /api/users/me/profile-picture`
- `DELETE /api/users/me/profile-picture`
- `GET /api/locations/suggestions`

Product/detail actions:

- `GET /api/v1/users/me/products/{canonicalProductKey}`
- `POST /api/v1/users/me/products:rehydrate`
- `POST /api/v1/users/me/products/{canonicalProductKey}/similar`
- `POST /api/v1/users/me/product-variant-selections`
- `GET /api/reviews/merchants/{merchantId}/products`
- saved-product endpoints under `/api/users/me/saved-products`
- discovery/suggestion/popular-search reads under `/api/users/me/...`

Taste profile and inventory:

- taste-profile reads, behavior writes, signal updates/removal, and suggestion accept/reject under `/api/users/me/taste-profile/...`
- inventory list/create/export under `/api/users/me/inventory`
- inventory update/delete under `/api/users/me/inventory/{itemId}`

Orders:

- `GET /api/orders`
- `GET /api/orders/{orderId}`

Merchant identity links:

- `GET /api/merchants/identity-links`
- `POST /api/merchants/{merchantId}/identity-link/authorization`
- `POST /api/merchants/identity-links/oauth/callback`
- `DELETE /api/merchants/identity-links/{merchantId}`

The identity-link authorization is a browser flow. Mobile needs an `expo-web-browser` auth session, state validation by the backend, and a deep-link callback route.

## Cart and Checkout Plan

Cart endpoints remain reusable:

- `POST /api/carts`
- `GET /api/carts/{cartId}`
- `PATCH /api/carts/{cartId}`
- `DELETE /api/carts/{cartId}`
- `POST /api/discounts/search`

Mobile must preserve exact server-issued offer keys, merchant-scoped cart partitions, account ownership checks, and stale-cart recovery. A cart `404`/`not_found` is a reconciliation event, not a raw user-facing error.

Checkout is selected by the backend response, not hardcoded by the client:

- `GET /api/carts/{cartId}/checkout`
- `PATCH /api/carts/{cartId}/checkout`
- `POST /api/carts/{cartId}/checkout/assistant`
- browser ECP lifecycle under `/api/carts/{cartId}/checkout/embedded/...`
- consent/direct-completion/cancel endpoints exist, but direct completion is not the default mobile MVP rail.

Phase 4 starts with a short contract spike and ends with one explicit implementation per `nextAction`/`selectedRail`:

- For eligible Shopify checkout URLs, prefer Shopify's React Native Checkout Sheet Kit (`@shopify/checkout-sheet-kit`) so merchant-controlled checkout can remain in the app.
- Reuse Meant's bootstrap/open/complete/cancel verification semantics only after the backend has a native-session binding. The current HTTP-Origin binding is browser-specific.
- For `HANDOFF` or unsupported embedded modes, open only backend-validated HTTPS merchant URLs through an in-app browser or system browser and reconcile checkout/cart state when the user returns.
- Keep direct checkout completion out of the MVP unless the backend capability policy enables it and a separate payment-instrument/security review approves the mobile credential flow.
- Record an explicit `mobile` checkout surface through a backend-defined field. Do not invent values in the app or reuse `meant_web_checkout`.
- Preserve sequential checkout for multi-merchant carts and make the current merchant/seller of record visible.

Because Meant sells physical goods, store billing is not the checkout mechanism; merchant/physical-goods payment methods are appropriate. Store policy review is still required before submission.

References:

- https://shopify.dev/docs/storefronts/mobile/checkout-kit
- https://developer.apple.com/app-store/review/guidelines/
- https://support.google.com/googleplay/android-developer/answer/9858738

## Environment Configuration

Only public configuration belongs in the app bundle:

```text
EXPO_PUBLIC_MEANT_API_URL=https://api.example.com
EXPO_PUBLIC_SUPABASE_URL=https://example.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=...
```

Use Expo config/EAS environments for development, preview, and production. Validate required values at startup and fail with a useful development error.

Local API routing:

- iOS simulator can normally use `http://localhost:8080`;
- Android emulator normally uses `http://10.0.2.2:8080`;
- physical devices need a LAN address or, preferably for auth/deep-link testing, an HTTPS tunnel/dev deployment.

Do not silently add broad cleartext-network exceptions to production builds. Native calls are not protected by browser CORS; security comes from TLS, JWT validation, authorization, request validation, and server-side capability policy.

## Implementation Phases

### Phase 0 — Contract and Monorepo Foundation

Deliverables:

- Add the root Bun workspace and migrate to one root `bun.lock`.
- Update frontend CI to install at the repository root and run all commands through Bun.
- Scaffold `mobile/` with the latest stable Expo SDK, Expo Router, TypeScript, lint, typecheck, and a placeholder app shell.
- Add mobile CI for lint/typecheck and `expo-doctor`.
- Regenerate and audit OpenAPI, including agent endpoints; add the root generation command and a drift check or documented exception.
- Add `packages/api-client` with the error/auth/fetch seams and one `/api/users/me` call.
- Add `packages/shared` only if a real first consumer exists.
- Document local simulator/device URLs and EAS monorepo command locations.

Acceptance:

- One frozen root install supports frontend and mobile.
- Existing frontend lint, format, tests, typecheck, and build remain green.
- Mobile lint/typecheck and `expo-doctor` pass.
- The mobile scaffold starts locally.
- No backend behavior changes are required for this phase.

### Phase 1 — Authenticated App Shell

Deliverables:

- Resolve iOS bundle identifier, Android application ID, app scheme, provider clients, and signing ownership.
- Implement Supabase persistence and foreground/background token refresh.
- Add Apple sign-in on iOS and Google sign-in on supported platforms via ID-token exchange.
- Create EAS development builds because Google auth and later Checkout Sheet integration require native code.
- Add auth gating, sign-out, callback routing, and `/api/users/me`.
- Add the React Query provider and the shared error/retry policy.

Acceptance:

- Google and iOS Apple sign-in work in real development builds.
- Mobile calls `/api/users/me` with the Supabase access token.
- Refresh, sign-out, account switching, foreground/background, and expired-session behavior are tested.
- No private secret appears in the JS bundle or committed configuration.

### Phase 2 — Durable Agent Discover Core

Deliverables:

- Conversation create/list/load/rename/archive/delete.
- Turn submission, authenticated event streaming, cursor persistence, Stop, reconnect, and snapshot recovery.
- A versioned event reducer shared with web where practical.
- Text, tool-activity, waiting, failure, cancellation, and terminal states.
- App lifecycle recovery when a run continues while mobile is backgrounded.

Acceptance:

- A user can hold a durable conversation, leave/reopen it, and recover the same transcript.
- Replayed/duplicate events do not duplicate content.
- Disconnect does not cancel the run; Stop does.
- No mobile code calls the removed qualification endpoint or routes commerce intent by keywords.

### Phase 3 — Product and Action Vertical Slice

Deliverables:

- Render agent product artifacts as native product lists/cards.
- Product detail, rehydration, variants, reviews, similar products, and canonical offer selection.
- Save/unsave and Saved tab.
- Compare and Shelf-equivalent context needed for conversational follow-up.
- Direct CTA actions with exact references and conversation action persistence where applicable.
- Empty/loading/error/rate-limit states.

Acceptance:

- The user can ask for products, receive grounded results, inspect one, select an exact offer, save it, compare it, and refer to it in a later turn.
- Product/cart UI derives from typed artifacts and shared pure mappings, not assistant prose.
- Historical messages retain their original artifact context after refresh.

### Phase 4 — Cart and Capability-Based Checkout

Deliverables:

- Merchant-partitioned cart, quantity updates/removal, discounts, stale-cart recovery, and agent cart reconciliation.
- Checkout assistant and saved-detail reuse where required.
- The native checkout contract spike and any narrowly required backend endpoint/lifecycle adaptation.
- Shopify React Native Checkout Sheet for eligible embedded checkout.
- Safe merchant handoff and return/reconciliation for fallback rails.
- Merchant identity-link browser/deep-link flow if an eligible merchant requires it.
- Multi-merchant sequential checkout and mobile surface attribution.

Acceptance:

- Cart mutations remain exact, merchant-scoped, and recoverable.
- Every backend checkout next action has an explicit safe mobile behavior.
- Embedded completion/cancel and merchant return are reconciled on physical iOS and Android devices.
- The agent prepares checkout but never completes payment autonomously.

### Phase 5 — Profile, Preferences, Inventory, Orders, and Compliance

Deliverables:

- Profile, newsletter, picture, settings, locations, and taste-profile editing.
- Inventory list/create/edit/delete/export and private photo upload.
- Orders list/detail.
- A backend-owned account-deletion flow, an in-app entry point, and a public deletion-request URL.
- Privacy policy, terms/support URLs, retention behavior, permission copy, and platform data disclosures.

Acceptance:

- Search-affecting preferences and inventory work without browser assumptions.
- Camera/library permissions and photo upload work on physical devices.
- Account deletion covers Supabase identity plus associated Meant data, subject to documented lawful retention.
- Apple and Google account-deletion requirements can be satisfied before store review.

References:

- https://developer.apple.com/support/offering-account-deletion-in-your-app/
- https://support.google.com/googleplay/android-developer/answer/13327111

### Phase 6 — Release Hardening and Distribution

Deliverables:

- `development`, `preview`, and `production` EAS profiles with pinned build/runtime policy.
- Internal iOS/Android builds and a physical-device regression matrix.
- App icons, splash, metadata, screenshots, support/privacy URLs, store privacy/data-safety answers, and review notes.
- Accessibility, reduced-motion, offline/poor-network, deep-link, background/resume, and crash-recovery passes.
- EAS Update only after a stable runtime-version policy exists; never send an update requiring native modules absent from the installed binary.

Acceptance:

- Team members can install preview builds.
- Production binaries are reproducible from the committed lockfile/configuration.
- Auth, agent recovery, product actions, cart, every checkout rail, uploads, deletion, and deep links pass on physical iOS and Android devices.

## Testing Strategy

From Phase 0:

- frontend regression suite through the root workspace;
- API error/auth-header/account-switch unit tests;
- pure shared helper and agent reducer tests;
- mobile lint, typecheck, and `expo-doctor`.

Add with each vertical slice:

- mocked transport tests for snapshot/replay/`410` recovery, duplicate cursors, abort, and unknown event types;
- product/artifact mapping and exact-offer tests;
- cart reconciliation and multi-merchant partition tests;
- checkout rail/URL policy/lifecycle tests;
- auth lifecycle and deep-link route tests where practical.

Physical-device release checks are mandatory for:

- Apple/Google sign-in and token refresh;
- authenticated SSE across background/foreground and network interruption;
- Checkout Sheet, merchant handoff, return, completion, and cancellation;
- image selection/upload and permissions;
- universal/app links and password/merchant callbacks.

Do not install a large end-to-end framework before the first flows stabilize. Add one when the stable critical path justifies its maintenance cost.

## Key Risks and Mitigations

### Contract drift

Risk: web, mobile, handwritten agent types, and generated OpenAPI diverge.

Mitigation: regenerate before extraction, check schema drift in CI, isolate any preview protocol, and keep versioned event parsing forward-compatible.

### Agent lifecycle on mobile

Risk: backgrounding, suspended networking, and replay cause missing or duplicated output.

Mitigation: durable server state, cursor persistence, idempotent reducer, foreground snapshot recovery, explicit Stop, and physical-device lifecycle tests.

### Native dependency and workspace resolution

Risk: Expo, React Native, Google auth, Checkout Sheet, or Bun layout produces duplicate/incompatible native dependencies.

Mitigation: explicit workspace dependencies, Expo-compatible versions, no global React override, `expo-doctor`, clean development builds, and an EAS build during each native dependency change.

### Checkout portability

Risk: the browser ECP/Origin contract is mistaken for a native checkout contract.

Mitigation: perform the Phase 4 contract spike, use Shopify's native Checkout Sheet only on an eligible rail, add a server-owned native session binding if needed, and retain safe merchant handoff.

### Auth/store configuration

Risk: identifiers, OAuth clients, signing fingerprints, deep links, account deletion, or privacy disclosures are left until submission.

Mitigation: resolve identifiers in Phase 1 and deletion/privacy work in Phase 5, with preview builds well before production review.

### Premature sharing

Risk: `packages/shared` becomes a dumping ground or couples two UIs.

Mitigation: share contracts and proven pure behavior only when both applications consume it; keep navigation, storage, auth initialization, and UI platform-owned.

## Revised First PR Scope

The first implementation PR should:

1. Add the root Bun workspace and single lockfile.
2. Update frontend CI commands to run from the root without changing frontend behavior.
3. Scaffold the Expo Router mobile app and add lint/typecheck/`expo-doctor` CI.
4. Regenerate/audit OpenAPI and establish the shared client package with `ApiError`, auth injection, fetch injection, and `/api/users/me`.
5. Add public environment validation and local device URL documentation.
6. Prove the frontend build and mobile local start.

Do not put social-provider configuration, the full agent UI, product search, cart, or checkout into this foundation PR. Native sign-in belongs in Phase 1 after identifiers/provider credentials are available.

## Resolved Decisions

- Mobile stays in this repository as a Bun workspace.
- Use Expo managed/CNG, Expo Router, TypeScript, React Query, and EAS development builds.
- Discover is agent-first and uses durable conversation/run APIs.
- Share API contracts, error behavior, the event reducer, and proven pure mappings; keep UI/auth/storage platform-specific.
- Checkout follows backend capability decisions and includes a merchant-handoff fallback.
- Push notifications are outside the first mobile release.

## Open Decisions

- Final iOS bundle identifier and Android application ID.
- Apple/Google developer accounts, signing ownership, OAuth clients, and production domain association files.
- Google sign-in package/tier and its long-term maintenance/licensing choice.
- Whether email/password sign-up and reset ship in mobile MVP.
- AsyncStorage versus a tested encrypted/chunked session adapter.
- The backend-native checkout session/lifecycle contract and accepted mobile attribution value.
- Account deletion retention rules and ownership of the public deletion-request page.
