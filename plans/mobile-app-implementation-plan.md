# Meant Mobile App Implementation Plan

Status: ready for engineering handoff; repository and platform guidance validated
Last validated: 2026-08-05
Repository baseline inspected: `4171f822` (2026-08-04)

Goal: add an iPhone-first Expo mobile app to this repository that reuses Meant's Spring Boot API, Supabase identity, durable commerce-agent protocol, and portable web domain logic. Expo managed/CNG and EAS are product constraints, not provisional scaffolding choices. The first foundation work should not change backend behavior; later phases may add narrow mobile-specific contracts where the current browser contract is not portable.

## Delivery Priorities

When scope or schedule conflicts arise, use this order:

1. Ship a polished, App-Store-ready iPhone experience through Expo and EAS.
2. Preserve backend contract correctness, account isolation, money safety, and durable agent recovery.
3. Keep Android buildable and avoid gratuitous iOS-only business logic, but do not make Android feature parity a gate for the first iOS release.

Version 1 is iPhone-only and targets iOS 16.4 or later. Set `ios.supportsTablet` to `false`; iPad support is a separate product/design scope. TestFlight and the Apple App Store are the first distribution targets. Android remains a supported follow-on surface and should receive CI smoke coverage from the start.

## Validity Assessment

The original direction is still sound:

- keep mobile in this repository;
- use Expo, Expo Router, TypeScript, and Bun workspaces;
- share generated API contracts and pure TypeScript logic, not web UI;
- use Supabase sessions to obtain bearer tokens for the Spring API;
- use React Query for request state while keeping ordinary client state in React;
- defer push notifications and unnecessary native customization.

Repository and platform validation found these corrections and constraints:

- Expo SDK 56 is the current stable baseline. It uses React Native 0.85 and React 19.2, requires Node 20.19 or later, and raises the minimum iOS version to 16.4 with Xcode 26.4 or later. The repository's Node 24 CI baseline is compatible.
- Expo SDK 56 installs `expo/fetch` as the native global `fetch`. Use an explicit `expo/fetch` import in stream code to make the transport dependency obvious, but do not add a second fetch polyfill.
- Discover is now backed by the durable server-side commerce agent. Mobile must implement conversations, runs, event replay, reconnection, and typed artifacts before treating discovery as complete.
- `POST /api/v1/users/me/product-search-qualifications` no longer exists. Qualification still exists internally, but the agent owns it. Mobile must not recreate the retired client-side qualify-then-search orchestrator.
- The checked-in `frontend/src/api/schema.d.ts` does not yet contain the agent endpoints, even though the controllers are live. OpenAPI regeneration/coverage is therefore a prerequisite to calling the generated schema the shared source of truth.
- The web agent now renders buyer-safe GitHub-Flavored Markdown plus grounded product links and visual product mentions. Mobile needs equivalent safe behavior without reusing the DOM renderer or matching arbitrary ungrounded product names.
- Checkout is capability-based. The backend can select embedded checkout, direct completion, or merchant handoff; direct completion is disabled by default and the agent does not complete payment. Mobile cannot promise that every checkout stays inside Meant.
- The current embedded-checkout bootstrap lifecycle is browser-specific and binds sessions to an allowed HTTP `Origin`. A native app needs an explicit mobile lifecycle contract or a documented alternative; it must not spoof a web origin.
- Shopify's newer `@shopify/checkout-kit-react-native` is documented as the successor to Checkout Sheet Kit but is not yet generally available. Use the generally available `@shopify/checkout-sheet-kit` for the MVP unless a Phase 0 compatibility spike and an ADR approve a later GA successor.
- The repository has no account-deletion endpoint or deletion-request page. Those are release blockers for an app that supports account creation.
- Apple native sign-in is an iOS path. Google native sign-in requires a development build; “both providers natively on both platforms” is not an accurate requirement.
- The former commerce-agent and Personal Commerce OS plan files were removed on 2026-08-02. They are not valid dependencies for this handoff document.

## Sources of Truth

This document owns mobile delivery sequencing. It does not redefine backend commerce behavior.

Use, in order:

1. Current backend controllers and their request/response records, especially `module/agent/controller`, `module/cart/controller`, `module/user/controller`, `module/order/controller`, and `module/merchant/controller`.
2. `frontend/src/lib/apiClient.ts` for the currently deployed handwritten agent wire contract and buyer-facing endpoint behavior.
3. Current frontend behavior and tests in `frontend/src/features/meant/agent`, `chat`, `product`, and `cart`, particularly `protocol.ts`, `eventReducer.ts`, `sse.ts`, `artifactMapping.ts`, `AgentMarkdown.tsx`, cart partitioning, and checkout policy.
4. Generated `frontend/src/api/schema.d.ts` only for endpoints it actually contains; do not infer missing agent contracts from it.
5. This document for mobile sequencing and platform decisions.

If this plan disagrees with running code, stop the affected slice, resolve the contract drift, update OpenAPI/tests, and record the decision before copying behavior into mobile. Do not use deleted historical plan paths as implementation authority.

## Current Repository Baseline

As of the validation date and inspected commit:

- There is no root `package.json`, root Bun lockfile, `mobile/`, or `packages/` directory.
- `frontend/` is a Bun application with its own `bun.lock`; CI pins Bun 1.3.9 and Node 24.
- `frontend/src/lib/apiClient.ts` contains a mixture of generated OpenAPI types and handwritten wire types/helpers.
- `frontend/src/api/schema.d.ts` is generated from `/v3/api-docs`, but is currently stale with respect to the agent controllers.
- The commerce agent is enabled by default and the web Discover surface uses it.
- Agent conversations and events are durable on the server; disconnecting a stream does not cancel a run.
- The web client has a tested versioned event reducer, cursor-gap recovery, SSE parsing, artifact projection, buyer-safe Markdown, grounded product mentions, and direct-action persistence. These are behavioral references, not reusable UI.
- The backend returns RFC 7807 `ProblemDetail` responses with a machine-readable `code`; the web client represents these as `ApiError(status, code)`.
- Checkout returns `nextAction`, `selectedRail`, capability decisions, and typed ineligibility reasons. Web checkout supports embedded Checkout Kit plus a merchant-handoff fallback.
- Supabase Storage buckets already exist for profile pictures and private inventory photos.
- There is still no mobile project and no backend account-deletion API.

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

Do not add Nx or Turborepo initially. Expo supports standard Bun workspaces and automatic Metro configuration in current SDKs. Keep one root `bun.lock`; EAS chooses Bun when it sees the Bun lockfile. Scaffold on Expo SDK 56 and install Expo/native packages through `expo install` so versions match that SDK; pin the resolved dependency graph in the committed lockfile.

Do not force one React version across web and mobile. Mobile must use the React 19.2 version selected by Expo SDK 56 while web keeps its framework-compatible React version. Shared packages must avoid importing React Native, React DOM, or app-owned React instances unless they are intentionally platform-specific. Run `expo-doctor` after every Expo/native dependency change.

References:

- https://docs.expo.dev/guides/monorepos/
- https://docs.expo.dev/guides/using-bun/
- https://docs.expo.dev/build-reference/build-with-monorepos/
- https://docs.expo.dev/versions/v56.0.0/

## Mobile Stack

Use:

- Expo managed workflow with Continuous Native Generation;
- Expo SDK 56, React Native 0.85, React 19.2, Hermes, and the New Architecture;
- Node 24 in local/CI tooling, satisfying Expo's Node 20.19 minimum;
- TypeScript and Expo Router with stable Stack/Tabs APIs;
- React Native primitives and mobile-owned UI, with selective `@expo/ui` SwiftUI-backed controls where they materially improve iOS forms, sheets, menus, and dialogs;
- `@supabase/supabase-js` with a React Native storage adapter;
- the shared `openapi-fetch` client for ordinary JSON endpoints;
- SDK 56's `expo/fetch` implementation for authenticated streamed responses;
- `@tanstack/react-query` for snapshot/request state;
- a pure reducer for durable agent events and transient streaming state;
- `expo-dev-client` and EAS development builds from Phase 0 onward.

Expo Go may be used for a disposable scaffold smoke test only. It is not an acceptance environment: native provider configuration, Universal Links, Checkout Sheet, privacy manifests, and release entitlements require a Meant development build.

Use stable platform APIs for release-critical navigation. Expo Router native tabs are still alpha and must not be a required dependency for version 1. A Phase 0 UI spike may compare them with stable tabs, but the default decision is stable tabs with iOS labels, SF Symbols, native stacks, safe-area handling, and preserved navigation state.

Keep the native layer generated. A package that needs native code must provide Expo-compatible autolinking or a config plugin, pass `expo-doctor`, produce a clean CNG prebuild, and build on EAS iOS. Do not commit generated `ios/` or `android/` folders merely to patch a dependency; record an ADR before leaving CNG or maintaining custom native code.

Avoid initially:

- custom native modules maintained by Meant;
- checked-in `ios/` and `android/` directories unless a dependency proves CNG insufficient;
- alpha navigation or UI APIs on a release-critical path;
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
- buyer-safe Markdown normalization and artifact-grounded product-mention matching, without React DOM rendering;
- money and currency-minor-unit helpers;
- product/offer mapping and variant selection;
- cart partitioning, counts, and stale-cart reconciliation;
- order mapping;
- preference/filter helpers.

Do not move browser storage, DOM components, CSS, browser Checkout Kit adapters, Supabase initialization, or React Native components. Extract a helper only when mobile is about to use it, retaining the web tests during the move.

## Proposed Mobile Structure

```text
mobile/
  src/
    app/
      _layout.tsx
      +native-intent.tsx
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

Use the SDK 56 `src/app` Expo Router convention. Primary tabs remain Discover, Saved, Inventory, Cart, and Profile. Compare, orders, conversation history, product detail, preferences, and merchant authorization are nested native-stack flows. Preserve each tab's navigation state and merchant boundaries in cart and checkout even when the UI presents a coordinated multi-merchant experience.

The iOS shell must use safe areas, keyboard avoidance, Dynamic Type, VoiceOver labels/actions, light and dark appearance, reduced motion, and system back/swipe gestures. Do not copy responsive web layouts into React Native.

## Authentication Plan

Supabase remains the identity provider; Spring remains the resource server.

Implementation requirements:

- Configure `persistSession: true`, `autoRefreshToken: true`, and `detectSessionInUrl: false` on native.
- Follow Supabase's React Native lifecycle pattern: use `processLock` and start/stop token auto-refresh when `AppState` moves between foreground and background.
- Start implementation with AsyncStorage, which is the documented Supabase React Native auth path, but finish the production storage threat-model decision in Phase 1. If Keychain storage is selected, implement and test a chunked SecureStore adapter, clear stale credentials on account deletion/sign-out/first launch after reinstall as designed, and handle native size errors rather than assuming a universal 2048-byte limit.
- Use `EXPO_PUBLIC_SUPABASE_URL` and prefer `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY`. A legacy anon key is also public, but if retained temporarily, name and migration behavior must be explicit across web and mobile.
- Never ship a service-role key, OpenRouter key, Shopify secret, EAS token, or backend credential in an `EXPO_PUBLIC_` value.
- Make Sign in with Apple the primary social sign-in on iOS. Use `expo-apple-authentication` and `supabase.auth.signInWithIdToken({ provider: 'apple', ... })`; persist the name returned on first authorization, handle revoked credential state, and test on a physical device.
- Use a supported Google native sign-in package and `signInWithIdToken({ provider: 'google', ... })`. The native Google module requires a development build plus the correct iOS OAuth client and callback scheme; Android application ID and signing fingerprints are Phase 7 configuration.
- If Google or another third-party social login is offered for the user's primary account on iOS, Sign in with Apple must remain an equivalent option unless an App Review exception clearly applies.
- Treat email/password parity as a product decision, not a hidden development fallback. If included, implement sign-up, confirmation, password reset, and deep-link recovery as a complete supported path.
- Configure a custom scheme for development callbacks. Before the first external TestFlight build, also configure verified iOS Universal Links, `ios.associatedDomains`, the production HTTPS callback routes, and the hosted `/.well-known/apple-app-site-association` file. Treat arbitrary/stale incoming links defensively in `+native-intent.tsx`.
- Account deletion must revoke Sign in with Apple tokens where applicable, not only delete the Supabase/Meant account.

The final iOS bundle identifier and Apple team are Phase 0 inputs. Apple native sign-in needs that app identity/capability, and Google on iOS needs its platform OAuth client/callback configuration. Android identity is not an iOS release gate.

References:

- https://supabase.com/docs/guides/auth/quickstarts/react-native
- https://supabase.com/docs/reference/javascript/auth-signinwithidtoken
- https://supabase.com/docs/guides/auth/social-login/auth-apple
- https://docs.expo.dev/versions/latest/sdk/apple-authentication/
- https://docs.expo.dev/linking/ios-universal-links/
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
- Read the authenticated GET SSE stream with an explicit `expo/fetch` import and the Supabase bearer token. SDK 56 also installs this implementation as native global `fetch`; do not add `EventSource` or another fetch polyfill.
- Parse UTF-8 incrementally and handle chunk-split fields, LF/CRLF separators, comments/heartbeats, multiple events per chunk, and a final partial buffer.
- Persist the latest applied cursor by Supabase user ID and run ID; ignore duplicates and consume unknown same-version event types as no-ops.
- iOS is allowed to suspend the app and its socket after backgrounding. Stop/abort the local stream when the app becomes inactive or backgrounded without cancelling the server run. On foreground/reconnect, load authoritative run/conversation state first and replay after the last applied cursor; if event history is unavailable, recover fully from snapshots.
- A network disconnect does not cancel the server run. Only the explicit cancel endpoint or replacement-turn behavior does.
- Keep event-stream state in a deterministic reducer; use React Query for conversation/run snapshots and invalidation around terminal events.
- Render products, carts, checkout, comparisons, and other commerce output from typed artifacts. Do not scrape assistant prose.
- Render buyer-safe Markdown with no raw HTML or remote Markdown images. Allow safe external HTTPS links, but resolve internal product interactions only from unambiguous artifact-grounded product references. Tables must scroll horizontally and remain accessible to VoiceOver/Dynamic Type.
- Direct CTA clicks execute deterministic APIs and use `/actions` where required so the conversation observes the mutation without a model round trip.

`POST /api/v1/users/me/product-searches` and `POST /api/v1/users/me/product-searches:stream` remain catalog contracts that require a server-issued READY qualification. They are not the main mobile chat orchestration surface. Mobile should use them only for a deliberately designed non-agent fallback that has an actual way to obtain the required qualification.

Expo SDK 56 exposes streamed response bodies and `getReader()` on mobile. Verify event framing, abort, replay, background/foreground recovery, and long-running runs on a physical iPhone first; Android validation follows without changing the protocol.

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

The identity-link authorization is a browser flow. Use `expo-web-browser` `openAuthSessionAsync` so iOS presents `ASWebAuthenticationSession`, with backend-owned state validation and a verified callback route. Do not treat an ordinary in-app browser close as proof that authorization succeeded; reload the authoritative identity-link state.

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

Phase 0 includes a time-boxed native dependency/build spike; Phase 4 completes the backend lifecycle contract and one explicit implementation per `nextAction`/`selectedRail`:

- For eligible Shopify checkout URLs, use the generally available React Native Checkout Sheet Kit (`@shopify/checkout-sheet-kit`) so merchant-controlled checkout can remain in the app. Confirm Expo SDK 56/React Native 0.85 autolinking and an EAS iOS build before feature work depends on it.
- Shopify documents `@shopify/checkout-kit-react-native` as the ECP-based successor, but its migration guide is pending general availability as of this validation. Do not adopt it for production merely because the web app uses the separate `@shopify/checkout-kit` package; reconsider at Phase 4 and record an ADR if its status changes.
- Reuse Meant's bootstrap/open/complete/cancel verification semantics only after the backend has a native-session binding. The current HTTP-Origin binding is browser-specific.
- For `HANDOFF` or unsupported embedded modes, open only backend-validated HTTPS merchant URLs through an in-app browser or system browser and reconcile checkout/cart state when the user returns.
- Keep direct checkout completion out of the MVP unless the backend capability policy enables it and a separate payment-instrument/security review approves the mobile credential flow.
- Record an explicit `mobile` checkout surface through a backend-defined field. Do not invent values in the app or reuse `meant_web_checkout`.
- Preserve sequential checkout for multi-merchant carts and make the current merchant/seller of record visible.
- Never collect, proxy, log, or persist raw payment credentials in Meant. Checkout Sheet/the merchant handles payment entry and remains the seller of record.

Because Meant sells physical goods, store billing is not the checkout mechanism; merchant/physical-goods payment methods are appropriate. Store policy review is still required before submission.

## Apple App Store Commerce Assessment

The proposed model is compatible in principle with the current App Store rules, and Apple does not take an In-App Purchase commission on these merchant sales. App Review Guideline 3.1.3(e) says that physical goods or services consumed outside the app must use payment methods other than In-App Purchase, such as Apple Pay or traditional card entry. ECP/Checkout Sheet is the checkout transport and presentation layer; the actual transaction remains a merchant physical-goods payment. App Review's payment classification depends on what is sold and where it is consumed, not on whether the checkout integration is named ECP.

This conclusion has strict release conditions:

- The iOS checkout surface may sell physical goods only. Digital content, subscriptions, features consumed in the app, digital gift cards, NFTs, or other StoreKit-regulated items must not be routed through ECP. Add a backend-owned purchase-eligibility classification/guard before exposing any purchase CTA on iOS; it must also cover prohibited/restricted categories and regional or age constraints. An unknown, digital, or prohibited classification fails closed to no purchase CTA. Merchant handoff is allowed only for an already verified eligible physical good.
- The merchant must remain clearly identified as seller of record for each checkout. Show merchant name, merchant-specific totals, shipping/returns responsibility, and the fact that multi-merchant carts are separate transactions.
- Meant may receive an affiliate or merchant commission, but it must not act as a staged wallet, substitute merchant of record, or intermediary that captures and replays payment credentials. This is also material if Apple Pay appears inside merchant checkout.
- Do not add StoreKit/IAP for physical-goods checkout. Apple Pay may appear when the merchant/Shopify checkout is correctly configured, but it is not mandatory for Meant to create a separate Apple Pay flow merely to avoid IAP.
- Cross-merchant product search is not itself prohibited. The material design risk is Minimum Functionality: Meant must be a real native commerce product, not merely a shop directory, generic content aggregator, or list of outbound links.
- The native app must provide substantial Meant functionality—personalized agent discovery, comparison, saved items, inventory, native cart coordination, orders, and durable recovery. It must not be a thin WebView, generic shop directory, or collection of merchant links. Only the merchant-controlled payment step may be embedded web checkout or browser handoff.
- Embedded web checkout must use the platform WebKit stack supplied by the supported Shopify React Native SDK. Do not bundle an alternative browser engine.
- Review notes must explain that all purchasable items are physical goods, merchants remain sellers of record, payment is processed in merchant/Shopify checkout, Meant does not sell digital unlocks, and no App Store account purchase is bypassed. Provide a working demo account, deterministic test catalog/cart, and a checkout path that App Review can exercise without placing a real order.
- Catalog policy, prohibited/restricted goods, merchant terms, refunds/support routing, privacy/data sharing, and regional consumer-law obligations still require product/legal review. App Store compliance does not by itself settle payment-services, marketplace, tax, or consumer-protection obligations.

If the product later introduces a paid Meant subscription or premium digital features consumed in the app, assess that revenue stream separately under StoreKit rules. The physical-goods exception does not cover a digital Meant membership.

References:

- https://shopify.dev/docs/storefronts/mobile/checkout-kit
- https://shopify.dev/docs/agents/carts-and-checkout/checkout-kit
- https://developer.apple.com/app-store/review/guidelines/
- https://developer.apple.com/apple-pay/acceptable-use-guidelines-for-websites/
- https://support.google.com/googleplay/android-developer/answer/9858738

## Environment Configuration

Only public configuration belongs in the app bundle:

```text
EXPO_PUBLIC_MEANT_API_URL=https://api.example.com
EXPO_PUBLIC_SUPABASE_URL=https://example.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=...
```

Use typed `app.config.ts` plus EAS environments for development, preview, and production. Validate required public runtime values at startup and fail with a useful development error. Keep signing credentials and server secrets in EAS/CI secret storage, never in Expo public configuration.

Required iOS configuration decisions in Phase 0:

- `ios.bundleIdentifier`: final stable identifier, not a placeholder;
- `ios.supportsTablet: false` for the iPhone-only first release;
- `ios.deploymentTarget: "16.4"` to match Expo SDK 56;
- `scheme`: stable development callback scheme;
- `ios.associatedDomains`: production Universal Link domains before external TestFlight;
- `ios.usesAppleSignIn: true` and the Apple Authentication config plugin when Apple sign-in lands;
- explicit app version/build-number ownership and `runtimeVersion` policy before EAS Update is enabled;
- user-facing camera/photo permission strings and removal of unused permissions;
- `ios.privacyManifests` entries required by Meant and third-party native dependencies;
- an explicit export-compliance answer based on the final dependency set; do not set `usesNonExemptEncryption` blindly.

Local API routing:

- iOS simulator can normally use `http://localhost:8080`;
- Android emulator normally uses `http://10.0.2.2:8080`;
- physical devices need a LAN address or, preferably for auth/deep-link testing, an HTTPS tunnel/dev deployment.

Do not silently add broad App Transport Security/cleartext-network exceptions to production builds. Native calls are not protected by browser CORS; security comes from TLS, JWT validation, authorization, request validation, and server-side capability policy.

References:

- https://docs.expo.dev/guides/apple-privacy/
- https://docs.expo.dev/guides/permissions/
- https://docs.expo.dev/build-reference/app-versions/

## Implementation Phases

### Phase 0 — Contract and Monorepo Foundation

Deliverables:

- Add the root Bun workspace and migrate to one root `bun.lock`.
- Update frontend CI to install at the repository root and run all commands through Bun.
- Scaffold `mobile/` on Expo SDK 56 with `src/app`, Expo Router, TypeScript, `expo-dev-client`, lint, typecheck, and a placeholder iPhone shell.
- Set the final iOS bundle identifier, iOS 16.4 deployment target, `supportsTablet: false`, app scheme, EAS project ownership, and stable Stack/Tabs navigation. Do not use alpha native tabs as the baseline.
- Add mobile CI for lint, typecheck, `expo-doctor`, and a noninteractive iOS JS bundle/export check.
- Add EAS `development` and `preview` profiles and produce the first CNG iOS development build; do not commit generated native folders.
- Regenerate and audit OpenAPI, including agent endpoints; add the root generation command and a drift check or documented exception.
- Add `packages/api-client` with the error/auth/fetch seams and one `/api/users/me` call.
- Add `packages/shared` only if a real first consumer exists.
- Document local simulator/device URLs and EAS monorepo command locations.
- Run a separate, time-boxed Expo SDK 56/React Native 0.85 iOS build spike for `@shopify/checkout-sheet-kit`; record autolinking/config-plugin results and the newer Checkout Kit GA status in an ADR without building checkout UI.

Acceptance:

- One frozen root install supports frontend and mobile.
- Existing frontend lint, format, tests, typecheck, and build remain green.
- Mobile lint/typecheck and `expo-doctor` pass.
- The mobile scaffold starts in an iOS simulator and a Meant development build installs on a physical iPhone.
- Stable tabs/stacks, safe areas, system back gestures, dark appearance, and large Dynamic Type render without clipping on a small iPhone screen.
- EAS builds from the workspace with Xcode 26.4 or later and the committed lockfile; `ios/` and `android/` remain generated artifacts.
- No backend behavior changes are required for this phase.

### Phase 1 — Authenticated App Shell

Deliverables:

- Confirm the Phase 0 iOS identity/signing owner, enable the Sign in with Apple capability, and configure the iOS provider clients/callbacks.
- Implement Supabase persistence and foreground/background token refresh.
- Add Sign in with Apple first, then Google sign-in if it is in MVP scope, through Supabase ID-token exchange.
- Configure development callback schemes and production Universal Links/AASA before external TestFlight distribution.
- Keep EAS development builds current whenever native auth configuration changes.
- Add auth gating, sign-out, callback routing, and `/api/users/me`.
- Add the React Query provider and the shared error/retry policy.
- Finish the session-storage threat model and document the production adapter decision.
- Define and schedule the backend account-deletion/revocation contract now, even if the full settings UI lands in Phase 5.

Acceptance:

- Sign in with Apple works in a real development build on a physical iPhone; Google works there too if included in MVP.
- Mobile calls `/api/users/me` with the Supabase access token.
- Refresh, sign-out, account switching, foreground/background, and expired-session behavior are tested.
- Custom-scheme callbacks work in development and production Universal Links work from a clean TestFlight install.
- No private secret appears in the JS bundle or committed configuration.

### Phase 2 — Durable Agent Discover Core

Deliverables:

- Conversation create/list/load/rename/archive/delete.
- Turn submission, authenticated event streaming, cursor persistence, Stop, reconnect, and snapshot recovery.
- A versioned event reducer shared with web where practical.
- Text, tool-activity, waiting, failure, cancellation, and terminal states.
- iOS lifecycle handling that aborts only the local stream on background and recovers the still-running server run on foreground.

Acceptance:

- A user can hold a durable conversation, leave/reopen it, and recover the same transcript.
- Replayed/duplicate events do not duplicate content.
- Disconnect does not cancel the run; Stop does.
- A run started on a physical iPhone recovers after screen lock, app switching, process termination, poor connectivity, and account switching without cross-account state leakage.
- No mobile code calls the removed qualification endpoint or routes commerce intent by keywords.

### Phase 3 — Product and Action Vertical Slice

Deliverables:

- Render agent product artifacts as native product lists/cards.
- Render safe Markdown, scrollable tables, safe external links, and unambiguous artifact-grounded product mentions with native product-detail navigation.
- Product detail, rehydration, variants, reviews, similar products, and canonical offer selection.
- Save/unsave and Saved tab.
- Compare and Shelf-equivalent context needed for conversational follow-up.
- Direct CTA actions with exact references and conversation action persistence where applicable.
- Empty/loading/error/rate-limit states.

Acceptance:

- The user can ask for products, receive grounded results, inspect one, select an exact offer, save it, compare it, and refer to it in a later turn.
- Product/cart UI derives from typed artifacts and shared pure mappings, not assistant prose.
- Historical messages retain their original artifact context after refresh.
- Product lists, image states, tables, and variant selection pass VoiceOver, Dynamic Type, dark mode, and small-screen checks on iOS.

### Phase 4 — Cart and Capability-Based Checkout

Deliverables:

- Merchant-partitioned cart, quantity updates/removal, discounts, stale-cart recovery, and agent cart reconciliation.
- Checkout assistant and saved-detail reuse where required.
- The backend-native checkout session contract and any narrowly required endpoint/lifecycle adaptation; do not reuse browser `Origin` as a native trust signal.
- A backend-owned iOS purchase-eligibility guard that allows only eligible physical goods through non-IAP checkout and fails closed for unknown, digital, prohibited, or region/age-ineligible goods.
- The approved generally available Shopify React Native Checkout Sheet dependency for eligible embedded checkout.
- Safe merchant handoff and return/reconciliation for fallback rails.
- Merchant identity-link browser/deep-link flow if an eligible merchant requires it.
- Multi-merchant sequential checkout and mobile surface attribution.

Acceptance:

- Cart mutations remain exact, merchant-scoped, and recoverable.
- Every backend checkout next action has an explicit safe mobile behavior.
- Embedded completion/cancel, offsite payment return, app termination during checkout, and merchant handoff are reconciled on a physical iPhone.
- The seller of record and separate transaction boundary are visible for every merchant; Meant never receives raw payment credentials.
- No digital or unknown-classification item can reach ECP/Checkout Sheet on iOS.
- The agent prepares checkout but never completes payment autonomously.

### Phase 5 — Profile, Preferences, Inventory, Orders, and Compliance

Deliverables:

- Profile, newsletter, picture, settings, locations, and taste-profile editing.
- Inventory list/create/edit/delete/export and private photo upload.
- Orders list/detail.
- A backend-owned account-deletion flow, an in-app entry point, and a public deletion-request URL.
- Sign in with Apple token revocation as part of deletion where applicable.
- Privacy policy, terms/support URLs, retention behavior, merchant data-sharing disclosure, permission copy, privacy manifest, and App Store privacy answers.

Acceptance:

- Search-affecting preferences and inventory work without browser assumptions.
- Camera/library permissions and photo upload work on physical devices.
- Account deletion covers Supabase identity plus associated Meant data, subject to documented lawful retention.
- Apple account-deletion requirements, including Apple token revocation where applicable, are satisfied before store review.
- The app asks only for permissions used by a visible feature, explains them with final product copy, and remains usable when optional photo access is denied.

References:

- https://developer.apple.com/support/offering-account-deletion-in-your-app/
- https://support.google.com/googleplay/android-developer/answer/13327111

### Phase 6 — iOS Release Hardening and App Store Distribution

Deliverables:

- Finalize `development`, `preview`, and `production` EAS profiles with pinned build/runtime policy, remote build-number ownership, and EAS Submit configuration.
- Internal and external TestFlight builds plus the iOS device/OS regression matrix.
- App icons, splash, metadata, iPhone screenshots, support/privacy URLs, age rating, App Privacy answers, privacy-manifest validation, and detailed review notes for agent behavior and physical-goods checkout.
- Accessibility, reduced-motion, offline/poor-network, deep-link, background/resume, and crash-recovery passes.
- Confirm the production build uses the iOS 26 SDK or later. Expo SDK 56's Xcode 26.4 baseline satisfies Apple's post-April-2026 upload requirement.
- Submit a TestFlight build early enough to receive Apple's missing-required-reason/privacy-manifest diagnostics and fix them before the release candidate.
- Ship no third-party tracking SDK in version 1 unless product explicitly approves it and ATT/App Privacy behavior is designed and reviewed.
- EAS Update only after a stable runtime-version policy exists; never send an update requiring native modules absent from the installed binary.

Acceptance:

- Team members can install preview builds.
- Production binaries are reproducible from the committed lockfile/configuration.
- Auth, agent recovery, product actions, cart, every checkout rail, uploads, deletion, and Universal Links pass on the supported physical-iPhone matrix.
- App Review can exercise a deterministic physical-goods checkout without a real charge and understands merchant/seller-of-record boundaries from the review notes.
- No unresolved App Store Connect privacy-manifest, required-reason API, export-compliance, age-rating, account-deletion, or metadata issue remains.

### Phase 7 — Android Parity and Play Distribution

Android stays buildable throughout Phases 0–6, but feature-complete Android release work follows the iOS release unless staffing allows parallel execution without delaying it.

Deliverables:

- Android application ID, signing, App Links, Google provider setup, permissions, data-safety answers, and Play release profiles.
- Feature parity and physical-device testing for auth, agent recovery, product flows, cart, checkout rails, uploads, deletion, and deep links.
- Platform-specific UI adjustments without moving business rules out of shared packages.

Acceptance:

- Android reaches the same contract, money-safety, account-isolation, and recovery guarantees as iOS.
- Play distribution work does not regress the shipped iOS app or force shared UI where native behavior differs.

## Testing Strategy

From Phase 0:

- frontend regression suite through the root workspace;
- API error/auth-header/account-switch unit tests;
- pure shared helper and agent reducer tests;
- mobile lint, typecheck, `expo-doctor`, iOS bundle/export, and recurring EAS iOS development builds after native dependency/config changes.

Add with each vertical slice:

- mocked transport tests for chunk-split SSE, LF/CRLF framing, snapshot/replay/`410` recovery, cursor gaps/duplicates, abort, and unknown event types;
- product/artifact mapping and exact-offer tests;
- safe Markdown and grounded-product-mention tests, including ambiguous names and malicious links/HTML;
- cart reconciliation and multi-merchant partition tests;
- checkout rail/URL policy/lifecycle, physical-goods eligibility, and seller-of-record tests;
- auth lifecycle and deep-link route tests where practical.

The minimum iOS matrix is:

- an iPhone SE-sized simulator on iOS 16.4/16.x for the oldest supported OS and smallest supported layout;
- a current-size physical iPhone on the latest stable iOS 26.x release through a TestFlight build;
- a second current large-screen/Dynamic-Island simulator for layout coverage;
- VoiceOver, large accessibility text, dark mode, reduced motion, low-power mode, poor/offline network, camera/photo denial, and an IPv6-only/NAT64 network pass on critical flows.

Physical-iPhone release checks are mandatory for:

- Apple/Google sign-in and token refresh;
- authenticated SSE across screen lock, background/foreground, process termination, and network interruption;
- Checkout Sheet, offsite payment, merchant handoff, return, completion, cancellation, and app termination;
- image selection/upload and permissions;
- clean-install Universal Links and password/merchant callbacks;
- account deletion and Sign in with Apple revocation.

Do not install a large end-to-end framework before the first flows stabilize. Add a small release smoke suite only when the stable critical path justifies its maintenance cost; it complements rather than replaces physical TestFlight checks.

## Key Risks and Mitigations

### Contract drift

Risk: web, mobile, handwritten agent types, and generated OpenAPI diverge.

Mitigation: regenerate before extraction, check schema drift in CI, isolate any preview protocol, and keep versioned event parsing forward-compatible.

### Agent lifecycle on mobile

Risk: backgrounding, suspended networking, and replay cause missing or duplicated output.

Mitigation: durable server state, cursor persistence, idempotent reducer, foreground snapshot recovery, explicit Stop, and physical-device lifecycle tests.

### Native dependency and workspace resolution

Risk: Expo, React Native, Google auth, Checkout Sheet, or Bun layout produces duplicate/incompatible native dependencies.

Mitigation: Expo SDK 56-compatible workspace dependencies, no global React override, `expo-doctor`, clean CNG development builds, and an EAS iOS build during each native dependency change. Keep release-critical alpha APIs out of the baseline.

### iOS 16.4 deployment floor

Risk: Expo SDK 56 drops devices that cannot run iOS 16.4, reducing the addressable install base.

Mitigation: accept iOS 16.4 as an explicit greenfield product decision, state it in release support policy, and revisit only with actual market/user evidence. Do not downgrade Expo reactively after implementation starts.

### Checkout portability

Risk: the browser ECP/Origin contract is mistaken for a native checkout contract.

Mitigation: perform the native dependency build spike in Phase 0, finalize the backend native-session contract in Phase 4, use Shopify's approved native Checkout Sheet only on an eligible rail, and retain safe merchant handoff.

### App Store commerce classification

Risk: a digital/unknown item is routed through ECP, Meant appears to be the seller or a staged wallet, or App Review sees the app as a thin shop-link aggregator.

Mitigation: fail-closed backend physical-goods eligibility, explicit merchant/seller-of-record UI, no payment credential handling, substantial native Meant features, deterministic review fixtures, and detailed App Review notes. Re-review Store policy whenever purchasable product categories or Meant's revenue model change.

### Auth/store configuration

Risk: identifiers, OAuth clients, signing fingerprints, deep links, account deletion, or privacy disclosures are left until submission.

Mitigation: resolve the iOS identity/signing owner in Phase 0, define deletion in Phase 1, complete its UI/privacy work in Phase 5, and send preview/TestFlight builds well before production review.

### Premature sharing

Risk: `packages/shared` becomes a dumping ground or couples two UIs.

Mitigation: share contracts and proven pure behavior only when both applications consume it; keep navigation, storage, auth initialization, and UI platform-owned.

## Revised First PR Scope

The first implementation PR should:

1. Add the root Bun workspace and single lockfile.
2. Update frontend CI commands to run from the root without changing frontend behavior.
3. Scaffold the Expo SDK 56 Router app under `mobile/src/app`, configure iPhone-only iOS 16.4, stable tabs/stacks, `expo-dev-client`, and add lint/typecheck/`expo-doctor`/iOS-export CI.
4. Regenerate/audit OpenAPI and establish the shared client package with `ApiError`, auth injection, fetch injection, and `/api/users/me`.
5. Add typed public environment validation, initial EAS development/preview profiles, and local simulator/device URL documentation.
6. Prove the frontend build, iOS simulator start, and one CNG EAS iOS development build from the committed workspace.

Do not put social-provider configuration, the full agent UI, product search, cart, or checkout into this foundation PR. Native sign-in belongs in Phase 1 after identifiers/provider credentials are available. Run Checkout Sheet compatibility as a separate Phase 0 spike PR/ADR so it cannot destabilize the foundation change.

## Resolved Decisions

- Mobile stays in this repository as a Bun workspace.
- Use Expo SDK 56 managed/CNG, Expo Router, TypeScript, React Query, `expo-dev-client`, and EAS; generated native directories are not source-controlled by default.
- Version 1 is iPhone-first, iOS 16.4+, and `supportsTablet: false`; Android release parity follows iOS.
- Stable Expo Router stacks/tabs are the release baseline. Alpha native tabs are optional experimentation only.
- Discover is agent-first and uses durable conversation/run APIs.
- Share API contracts, error behavior, the event reducer, and proven pure mappings; keep UI/auth/storage platform-specific.
- Checkout follows backend capability decisions, uses non-IAP merchant payment for physical goods, exposes seller-of-record boundaries, and includes a merchant-handoff fallback.
- Use GA `@shopify/checkout-sheet-kit` for the MVP if the SDK 56 build spike passes; do not adopt its ECP successor before GA/ADR approval.
- Sign in with Apple is the primary iOS social-login path; production Universal Links are required before external TestFlight.
- Push notifications are outside the first mobile release.

## Open Decisions

- Final iOS bundle identifier, app name/slug/scheme, Apple Developer organization, App Store Connect ownership, and EAS project owner. These must be supplied before Phase 0 acceptance.
- Android application ID and Play signing ownership before Phase 7.
- Apple/Google provider clients, signing ownership, OAuth configuration, and production domain association files.
- Google sign-in package/tier and its long-term maintenance/licensing choice.
- Whether email/password sign-up and reset ship in mobile MVP.
- AsyncStorage versus a tested encrypted/chunked session adapter.
- The backend-native checkout session/lifecycle contract and accepted mobile attribution value.
- The backend field/taxonomy that proves an item is a physical good eligible for non-IAP iOS checkout.
- Account deletion retention rules and ownership of the public deletion-request page.
- Launch locales and whether English-only is acceptable for version 1.
