# Meant Mobile App Implementation Plan

Goal: add an Expo mobile app to this repository without changing backend behavior first. The mobile app should reuse the existing backend API, Supabase authentication model, and web client domain logic where that reuse is actually portable.

## Recommendation

Use the existing repository as a monorepo and add a new `mobile/` workspace.

This is the better fit for Meant right now because the mobile product will be tightly coupled to the existing Spring Boot API contract, Supabase auth setup, product search flow, cart flow, saved products, inventory, and orders. The main technical risk is API/model drift between web and mobile, not mobile release isolation.

A separate mobile repository would be reasonable later if the mobile app gets a separate team, separate compliance process, or a release process that becomes painful inside this repo. That is not the current shape.

## Research Notes

- Expo has first-class monorepo support for package-manager workspaces. Expo detects workspace configuration and, with current SDKs, configures Metro automatically for standard monorepo setups.
  Reference: https://docs.expo.dev/guides/monorepos/
- Expo Router is the recommended file-based routing model for Expo apps and gives us typed routes, deep linking, and route-based organization.
  Reference: https://docs.expo.dev/router/introduction/
- Expo's `versions/latest` documentation and `create-expo-app` flow should be used at scaffold time so the app starts on the latest stable Expo SDK available then, not a stale SDK pinned in this planning document.
  Reference: https://docs.expo.dev/versions/latest/
- Expo public environment variables must use `EXPO_PUBLIC_` and are embedded into the app bundle, so they must never contain secrets.
  Reference: https://docs.expo.dev/guides/environment-variables/
- EAS Build is the standard hosted build path for Expo/React Native binaries, supports build profiles, internal distribution, app signing credentials, and app store submission integration.
  Reference: https://docs.expo.dev/build/introduction/
- Supabase's Expo React Native guidance uses `@supabase/supabase-js` with React Native storage for persisted sessions and `EXPO_PUBLIC_SUPABASE_URL` / publishable key configuration.
  Reference: https://supabase.com/docs/guides/getting-started/tutorials/with-expo-react-native

## Current Repository Observations

The existing repo already has:

- `backend/`: Spring Boot API with authenticated `/api/...` endpoints.
- `frontend/`: React web app using Supabase auth and backend API calls.
- `frontend/src/api/schema.d.ts`: generated OpenAPI types from `http://localhost:8080/v3/api-docs`.
- `frontend/src/lib/apiClient.ts`: web API client using `openapi-fetch` and a Supabase bearer token middleware.
- `frontend/src/lib/supabase.ts`: web Supabase client using browser session persistence.
- Web feature folders that map to the same domains the mobile app needs:
  - `auth`
  - `chat`
  - `ask`
  - `product`
  - `cart`
  - `inventory`
  - `orders`
  - `account`
  - `preferences`

The backend already expects `Authorization: Bearer <supabase-access-token>` for authenticated business endpoints. Mobile should follow that same contract.

## Target Repository Shape

Start with a light workspace structure:

```text
backend/
frontend/
mobile/
packages/
  api-client/
  shared/
```

Do not introduce Nx or Turborepo in the first pass. Expo already supports standard workspaces, and we do not yet have enough cross-package build complexity to justify another orchestration layer.

Add root workspace metadata only when implementing:

```json
{
  "private": true,
  "workspaces": [
    "frontend",
    "mobile",
    "packages/*"
  ]
}
```

Use Bun workspaces by default. The existing frontend already has `frontend/bun.lock`, uses `bun test`, and CI installs frontend dependencies with Bun, so introducing npm or pnpm as a second JavaScript package manager would add friction. The first implementation PR should migrate to a single root `bun.lock`, update CI to install from the repository root, and verify that EAS Build installs the Expo workspace correctly. If EAS Build exposes a Bun-specific blocker, fall back deliberately to npm workspaces in that same PR rather than mixing package managers long term.

## Mobile App Stack

Use:

- Expo managed app
- Latest stable Expo SDK available at scaffold time
- TypeScript
- Expo Router
- React Native primitives, not shared web UI components
- Supabase JS client for auth
- `openapi-fetch` or an equivalent generated client around the existing OpenAPI schema
- EAS Build/Submit/Update once the app is ready for device distribution

Avoid in the first version:

- custom native modules
- prebuild unless a required package forces it
- shared React UI components between web and mobile
- a complex app-wide state framework before the data flows prove it is needed
- push notifications

## Workspace Packages

### `packages/api-client`

Purpose: one typed backend contract used by both web and mobile.

Responsibilities:

- Own generated OpenAPI types from the backend.
- Export typed endpoint helper functions where they are genuinely shared.
- Accept platform-specific auth token lookup and base URL as configuration.
- Avoid importing browser-only or React Native-only APIs.

Proposed shape:

```text
packages/api-client/
  src/
    schema.d.ts
    createMeantApiClient.ts
    errors.ts
    users.ts
    products.ts
    cart.ts
    orders.ts
  package.json
  tsconfig.json
```

Design:

```ts
export interface MeantApiClientOptions {
  baseUrl: string
  getAccessToken: () => Promise<string | null>
}
```

The web app passes a `getAccessToken` implementation backed by browser Supabase. The mobile app passes one backed by the Expo/Supabase client. This avoids leaking platform storage concerns into the shared API package.

Generation command should be root-friendly:

```text
generate API types from backend /v3/api-docs into packages/api-client/src/schema.d.ts
```

During implementation, replace or wrap the current `frontend/src/api/schema.d.ts` usage with the shared package. Do this only after the mobile scaffold exists so we do not break web for a packaging refactor.

### `packages/shared`

Purpose: pure TypeScript logic shared across web and mobile.

Good candidates:

- money formatting helpers
- product mapping and normalization
- product offer selection
- preference/filter helpers
- cart pure helpers
- order mapping
- location data if it is not tied to browser APIs

Bad candidates:

- React DOM components
- CSS-driven UI helpers
- browser storage helpers
- web-only animations
- mobile layout components
- Supabase client initialization

Move code into `packages/shared` incrementally. Do not start by moving every existing helper. First move only the helpers needed by the first mobile vertical slice.

## Mobile App Structure

Proposed `mobile/` layout:

```text
mobile/
  app/
    _layout.tsx
    (auth)/
      sign-in.tsx
      sign-up.tsx
    (tabs)/
      _layout.tsx
      index.tsx
      saved.tsx
      inventory.tsx
      cart.tsx
      profile.tsx
    product/
      [productKey].tsx
    order/
      [orderId].tsx
  src/
    api/
      client.ts
    auth/
      supabase.ts
      useSession.ts
    features/
      discover/
      product/
      saved-products/
      inventory/
      cart/
      profile/
      orders/
    components/
    theme/
    lib/
  app.json
  eas.json
  package.json
  tsconfig.json
```

Main navigation:

- `Discover`: primary hybrid chat and product search entry point, matching the current web app behavior.
- `Saved`: saved products and shopping memory.
- `Inventory`: owned items and upload flow.
- `Cart`: active cart/checkout state.
- `Profile`: account, settings, preferences, sign out.

## Auth Plan

Mobile should use Supabase as the identity provider and backend should remain the resource server.

Implementation requirements:

- Use `@supabase/supabase-js`.
- Configure mobile session persistence with React Native-compatible storage.
- Use `EXPO_PUBLIC_SUPABASE_URL`.
- Use `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY` or the current Supabase public anon/publishable key naming we standardize on.
- Do not store any private Supabase service role key or backend secret in the mobile app.
- Implement Google and Apple sign-in immediately for the MVP auth path.
- Configure OAuth redirect/deep-link handling during the first auth implementation, not as a later enhancement.
- Treat email/password as an optional development fallback only, not the MVP user-facing auth path.

Backend API calls:

```text
Authorization: Bearer <current Supabase access token>
```

Mobile-specific Supabase setup must stay in `mobile/src/auth/supabase.ts`; web-specific setup stays in the web app. Shared packages may receive auth tokens, but must not know how sessions are stored.

## API Integration Plan

Use the backend as the source of truth. Mobile should not call UCP/plugin transport endpoints directly.

Primary mobile endpoint groups:

- Profile/settings:
  - `GET /api/users/me`
  - `PATCH /api/users/me`
  - `GET /api/users/me/settings`
  - `PATCH /api/users/me/settings`
- Product discovery:
  - `POST /api/users/me/product-searches`
  - `GET /api/users/me/product-discovery`
  - `GET /api/users/me/product-search-suggestions`
  - `GET /api/users/me/popular-product-searches`
- Assistant:
  - `GET /api/users/me/assistant/conversations`
  - `GET /api/users/me/assistant/conversations/latest`
  - `GET /api/users/me/assistant/conversations/{conversationId}`
  - `POST /api/users/me/assistant/messages:stream`
- Saved products:
  - `GET /api/users/me/saved-products`
  - `POST /api/users/me/saved-products`
  - `DELETE /api/users/me/saved-products`
- Inventory:
  - `GET /api/users/me/inventory`
  - `POST /api/users/me/inventory`
  - `POST /api/users/me/inventory/photos`
  - `PATCH /api/users/me/inventory/{itemId}`
  - `DELETE /api/users/me/inventory/{itemId}`
- Cart:
  - `POST /api/carts`
  - `GET /api/carts/{cartId}`
  - `PATCH /api/carts/{cartId}`
  - `DELETE /api/carts/{cartId}`
  - checkout endpoints to be finalized by the in-app checkout rework
- Orders:
  - `GET /api/orders`
  - `GET /api/orders/{orderId}`

Streaming endpoints need mobile-specific verification. The first discovery screen must still feel like the current app: a hybrid chat and product search surface. Prefer non-streaming product search for the first result-loading path where possible, then add streaming assistant behavior after testing on iOS and Android devices because React Native fetch/SSE support can differ from browser behavior.

## Environment Configuration

Mobile `.env` values should be public-only:

```text
EXPO_PUBLIC_MEANT_API_URL=https://api.example.com
EXPO_PUBLIC_SUPABASE_URL=https://example.supabase.co
EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY=...
```

Local development needs a device-aware API URL:

- iOS simulator can usually reach host machine services via `http://localhost:8080`.
- Android emulator usually needs `http://10.0.2.2:8080`.
- Physical devices need the LAN IP or a tunneled URL.

Do not hardcode this into the client. Use env files or Expo config variants.

Backend CORS is browser-origin focused and does not block native mobile requests in the same way. Keep CORS config for web. Mobile auth security comes from JWT validation, not CORS.

## Release and Build Plan

Add EAS configuration after the initial local Expo app runs.

Suggested profiles:

```text
development: development client builds for local testing
preview: internal distribution for team testing
production: app store builds
```

Use EAS Update only after the app has a stable runtime version policy. Avoid shipping updates that assume native modules not present in the installed binary.

Do not wire app store submission in the first scaffold PR. Add it after we have bundle identifiers, Apple/Google accounts, signing ownership, privacy answers, and a real release candidate.

Push notifications are explicitly out of scope for the first mobile release.

## Implementation Phases

### Phase 0 - Monorepo Foundation

Deliverables:

- Add root workspace config.
- Use Bun workspaces and migrate to a single root `bun.lock`.
- Update frontend CI commands to run from the workspace root or explicitly target the `frontend` workspace.
- Add `mobile/` Expo app scaffold with the latest stable Expo SDK available at scaffold time.
- Add `packages/api-client` placeholder or first version.
- Add `packages/shared` placeholder only if the first vertical slice needs it.
- Keep backend untouched.

Acceptance:

- Web still installs and builds.
- Mobile starts with Expo locally.
- TypeScript project references or package exports are understandable and documented.

### Phase 1 - Auth and API Client

Deliverables:

- Mobile Supabase client with persisted session.
- Google and Apple sign-in.
- Deep-link/OAuth callback handling for development builds and production builds.
- Optional email/password fallback only if it materially speeds local development.
- Shared API client that injects the Supabase bearer token.
- `GET /api/users/me` wired in mobile.
- Basic authenticated/unauthenticated route gating.

Acceptance:

- User can sign in on mobile with Google and Apple.
- Mobile can call backend `/api/users/me`.
- Expired sessions refresh without manual user intervention.
- Sign out clears mobile session.

### Phase 2 - Product Discovery Vertical Slice

Deliverables:

- Discover screen matching the current web app's hybrid chat and search behavior.
- Direct search input.
- Chat-style assistant/discovery surface.
- Call `POST /api/users/me/product-searches`.
- Product result list.
- Product detail screen.
- Save/unsave product flow.
- Basic empty/loading/error states.

Acceptance:

- User can search or use the chat-style discovery surface, view products, open detail, save a product, and see it in `Saved`.
- Product and price rendering uses shared pure helpers where practical.
- Streaming is not required for the first product result path, but the screen architecture must leave room for assistant streaming.

### Phase 3 - Cart and Checkout

Deliverables:

- Add to cart from product detail.
- Cart tab.
- Quantity updates/removal.
- In-app checkout flow once the checkout rework lands.
- Checkout completion/cancel state handling inside the app.

Acceptance:

- User can create/update/delete cart state against backend.
- Checkout stays inside the app and does not depend on a merchant redirect or external browser handoff.
- Cart recovery/error behavior matches web where applicable.

### Phase 4 - Profile, Preferences, and Inventory

Deliverables:

- Profile/settings screen.
- Preference editing.
- Inventory list.
- Inventory item create/edit/delete.
- Inventory photo upload if backend/Supabase storage flow is ready for mobile.

Acceptance:

- User can edit the settings that influence search.
- Inventory can be managed without web-only assumptions.
- Photo upload is verified on physical devices before release.

### Phase 5 - Assistant and Streaming

Deliverables:

- Conversation list/latest conversation.
- Assistant thread UI.
- Streaming message support if verified on device.
- Fallback to non-streaming interaction if streaming is unreliable.

Acceptance:

- Mobile chat behavior matches backend contract.
- Abort/retry behavior is explicit.
- Long responses do not freeze the UI.

### Phase 6 - EAS Distribution

Deliverables:

- `eas.json` profiles.
- Development build.
- Preview/internal build.
- Bundle identifiers and app metadata.
- Release checklist.

Acceptance:

- Team can install preview builds.
- Production build can be produced reproducibly.
- Secrets and public env values are separated.

## Reuse Strategy

Reuse immediately:

- OpenAPI schema generation pattern.
- `openapi-fetch` style client.
- API error parsing strategy.
- Pure product/cart/order mapping logic needed by the first mobile screens.
- Supabase bearer token injection pattern.

Do not reuse immediately:

- Web React components.
- CSS and animation systems.
- Browser storage helpers.
- Web routing.
- Web modal/product card layouts.

Refactor rule:

Only move code from `frontend/` into `packages/shared` when `mobile/` is about to use it. Do not perform a broad extraction ahead of product work.

## Testing Strategy

Initial test scope:

- Pure helpers in `packages/shared`.
- API client error handling and auth header behavior with mocked token getter.
- Mobile feature logic where it can be tested without a native runtime.

Later test scope:

- Expo app smoke tests.
- Device/manual QA checklist for auth, search, in-app checkout, image upload, and deep links.
- CI build validation for web and mobile TypeScript.

Do not add a large React Native testing stack until the first mobile flows stabilize.

## Risks and Mitigations

### Package manager and dependency duplication

Risk: monorepos can introduce duplicate React/React Native/native module versions.

Mitigation:

- Keep mobile dependencies explicit.
- Run dependency duplicate checks during setup.
- Avoid sharing packages that import React Native unless they are mobile-only.
- Keep `packages/shared` pure TypeScript.

### Streaming support

Risk: browser streaming assumptions may not hold in React Native.

Mitigation:

- Use non-streaming product search for the first result-loading path where possible.
- Keep assistant streaming as a separately verified mobile behavior inside the hybrid discovery surface.
- Verify SSE/fetch streaming on iOS and Android before committing assistant streaming UX.
- Provide fallback behavior.

### Shared code becoming a dumping ground

Risk: premature sharing makes both web and mobile harder to change.

Mitigation:

- Share contracts and pure logic first.
- Keep UI platform-specific.
- Move code only when two apps actively need it.

### Mobile auth redirects

Risk: OAuth/magic link redirect behavior differs across Expo Go, development builds, and production builds.

Mitigation:

- Implement Google and Apple sign-in through development builds early.
- Configure deep links as part of the initial auth work.
- Test redirects in a development build, not only Expo Go.

### In-app checkout rework

Risk: the mobile checkout target is changing from redirect/handoff behavior to an in-app checkout flow, so the mobile app could be built against a moving backend/API contract.

Mitigation:

- Treat checkout as Phase 3, after the backend checkout rework contract is clear.
- Keep cart state and checkout state boundaries explicit in the mobile client.
- Test checkout completion and cancellation on both iOS and Android against the final in-app API.

## First PR Scope

Recommended first implementation PR:

1. Add root workspace config.
2. Scaffold `mobile/` Expo app with TypeScript, Expo Router, and the latest stable Expo SDK available at scaffold time.
3. Add mobile Supabase setup with public env variables and Google/Apple OAuth wiring. If the final bundle identifier is still unknown, document which production Apple/EAS settings remain blocked.
4. Add a minimal typed API client wrapper or `packages/api-client` skeleton.
5. Implement auth route gating and a signed-in home screen that calls `/api/users/me`.
6. Document local development URLs for iOS simulator, Android emulator, and physical devices.

Do not implement product search, cart, or checkout in the first PR. The first PR should prove that monorepo, Expo, social auth wiring, and backend connectivity are sound.

## Resolved Product Decisions

- Expo SDK: use the latest stable Expo SDK available at scaffold time.
- MVP auth: Google and Apple sign-in are required immediately.
- First discovery surface: hybrid chat and product search, matching the current web app.
- Push notifications: not part of the first mobile release.
- Checkout: the mobile app should target the in-app checkout rework and should not rely on an external redirect or browser handoff.

## Open Decisions

- Mobile bundle identifier. This blocks final Apple sign-in configuration, EAS production metadata, and app store setup.
