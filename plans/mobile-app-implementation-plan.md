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

- `Discover`: primary search/chat/product discovery entry point.
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
- Add OAuth redirect/deep-link configuration only when social login is implemented.

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
  - `GET /api/carts/{cartId}/checkout`
  - `POST /api/carts/{cartId}/checkout/complete`
  - `POST /api/carts/{cartId}/checkout/cancel`
- Orders:
  - `GET /api/orders`
  - `GET /api/orders/{orderId}`

Streaming endpoints need mobile-specific verification. For MVP, prefer the non-streaming product search endpoint first. Add streaming chat/search only after testing behavior on iOS and Android devices, because React Native fetch/SSE support can differ from browser behavior.

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

## Implementation Phases

### Phase 0 - Monorepo Foundation

Deliverables:

- Add root workspace config.
- Use Bun workspaces and migrate to a single root `bun.lock`.
- Update frontend CI commands to run from the workspace root or explicitly target the `frontend` workspace.
- Add `mobile/` Expo app scaffold.
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
- Auth screens for email/password at minimum.
- Shared API client that injects the Supabase bearer token.
- `GET /api/users/me` wired in mobile.
- Basic authenticated/unauthenticated route gating.

Acceptance:

- New user can sign in on mobile.
- Mobile can call backend `/api/users/me`.
- Expired sessions refresh without manual user intervention.
- Sign out clears mobile session.

### Phase 2 - Product Discovery Vertical Slice

Deliverables:

- Discover screen with search input.
- Call `POST /api/users/me/product-searches`.
- Product result list.
- Product detail screen.
- Save/unsave product flow.
- Basic empty/loading/error states.

Acceptance:

- User can search, view products, open detail, save a product, and see it in `Saved`.
- Product and price rendering uses shared pure helpers where practical.
- No streaming dependency yet.

### Phase 3 - Cart and Checkout

Deliverables:

- Add to cart from product detail.
- Cart tab.
- Quantity updates/removal.
- Checkout handoff flow.
- Checkout completion/cancel state handling.

Acceptance:

- User can create/update/delete cart state against backend.
- Checkout URL handling works on iOS and Android.
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
- Device/manual QA checklist for auth, search, checkout handoff, image upload, and deep links.
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

- MVP uses non-streaming product search.
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

- Start with email/password auth.
- Add deep links when social login is prioritized.
- Test redirects in a development build, not only Expo Go.

### Checkout handoff

Risk: external merchant checkout URLs may behave differently in in-app browsers and system browsers.

Mitigation:

- Use system browser/deep link safe handling.
- Log and display recoverable errors.
- Test on both platforms with real merchant checkout URLs.

## First PR Scope

Recommended first implementation PR:

1. Add root workspace config.
2. Scaffold `mobile/` Expo app with TypeScript and Expo Router.
3. Add mobile Supabase setup with public env variables.
4. Add a minimal typed API client wrapper or `packages/api-client` skeleton.
5. Implement auth route gating and a signed-in home screen that calls `/api/users/me`.
6. Document local development URLs for iOS simulator, Android emulator, and physical devices.

Do not implement product search, cart, or checkout in the first PR. The first PR should prove that monorepo, Expo, auth, and backend connectivity are sound.

## Open Decisions

- Exact Expo SDK version at scaffold time.
- Mobile bundle identifier.
- Whether email/password is enough for MVP auth or Google/Apple login is required immediately.
- Whether the first product discovery screen should be search-first, chat-first, or a hybrid.
- Whether push notifications are part of the first mobile release.
- Whether checkout should open in the system browser or an in-app browser for the first release.
