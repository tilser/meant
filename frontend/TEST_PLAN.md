# Frontend Test Plan - Codex Assignment

Goal: cover logic where a silent regression would leave the user stuck or cause
data loss. The priority is **stale-cart recovery** (recently fixed but not yet
tested) and the **error-handling boundary** in `apiClient`, then the remaining
pure logic in `utils.ts`.

## Environment (important - follow this)

- **Runner: Bun built-in** (`bun test`). The existing test
  `src/features/meant/utils.test.ts` imports `import { describe, expect, test } from 'bun:test'`.
  Do not add Vitest, Jest, or RTL. They are not in the project, and we do not
  want new test infrastructure.
- Add a `"test": "bun test"` script to `package.json` so tests run consistently
  and can be wired into CI.
- **Do not test React components**. RTL/jsdom are not in the project. All logic
  under test must be a pure function. Where logic currently lives as a closure
  inside `MeantApp.tsx`, do a small **extract-and-export refactor** (see below).
  The refactor is part of the assignment, not an incidental side effect.
- No network calls in tests. Where `fetch` or `updateCart` is needed, pass the
  dependency as a parameter or mock it at the module level.

---

## Block 1 - apiClient Error Boundary (highest priority)

File: `src/lib/apiClient.ts`. This is where the previous silent bug happened:
the frontend matched on error text that the backend scrubs. Tests must lock this
down.

### 1A. `parseErrorResponse` -> typed `ApiError`

`parseErrorResponse` is currently **private**. Export it, or move it to
`src/lib/apiError.ts` together with `ApiError` and re-export it. Then test:

- [ ] **Status is preserved**: a ProblemDetail body with status 404 returns an
  `ApiError` with `status === 404`.
- [ ] **`code` is preserved**: body `{ code: "not_found", detail: "..." }`
  yields `error.code === "not_found"`.
- [ ] **`detail` takes priority over `title`/`message`** when building
  `message` (keep the existing priority: `detail || message || title || fallback`).
- [ ] **Fallback for a non-JSON body**: a response whose `.json()` throws
  returns `ApiError(fallback, status, null)` and never crashes.
- [ ] **Missing `code`**: body without `code` yields `error.code === null`
  (no crash, no leaking `undefined` downstream).
- [ ] `ApiError instanceof Error === true` (regression guard: other handlers
  read `error.message`).

### 1B. `parseJsonResponse`

- [ ] **2xx**: returns the parsed body as `T`.
- [ ] **Non-2xx throws `ApiError`** (not plain `Error`) with the correct
  `status`.
- [ ] The thrown error carries `message` from `detail`, not the fallback, when
  `detail` exists.

> Note: `parseJsonResponse` takes a `Response`. In Bun, build one with
> `new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })`.
> No fetch mock is needed.

---

## Block 2 - Stale-Cart Recovery (high priority, recently fixed)

The logic currently lives in `MeantApp.tsx` as closures
(`isCartNotFoundError`, `recreateMerchantCart`, `cartAddItemsForMerchant`,
`clearMerchantCartState`) and in pure helpers (`mergeCartSnapshot`,
`cartMerchantKey`). The closures touch React state, so they are not directly
testable.

### Prerequisite refactor (part of the assignment)

Extract the pure, React-independent core logic into `utils.ts` or a new
`cartRecovery.ts`, and export:

1. `isCartNotFoundError(error: unknown): boolean` - move it without changing
   behavior. Today the logic is
   `error instanceof ApiError && (error.status === 404 || error.code === 'not_found')`.
2. `cartRebuildItems(items, merchantKey)` - a pure version of the logic from
   `cartAddItemsForMerchant` / `recreateMerchantCart`: from items for the given
   merchant, produce `{ productVariantId, quantity }[]`, filter out items
   without `productVariantId`, and set `quantity = max(qty, 1)`.
3. `mergeCartSnapshot` and `cartMerchantKey` are already pure. Just export them.

The closures in `MeantApp` should then call these exported functions. Do not
duplicate the logic.

### Tests: `isCartNotFoundError`

- [ ] `ApiError` with `status: 404` -> **true**.
- [ ] `ApiError` with `code: "not_found"` and another status -> **true**.
- [ ] `ApiError` with `status: 400` / `code: "bad_request"` -> **false**.
- [ ] Plain `new Error("cart not found")` -> **false** (regression guard: do
  not rely on text again; that was the previous bug).
- [ ] `undefined` / `null` / `{}` -> **false**, no throw.

### Tests: `cartRebuildItems`

- [ ] Selects only items for the given `merchantKey`.
- [ ] Skips items without `productVariantId`.
- [ ] `qty: 0` or negative values become `quantity: 1` (clamp).
- [ ] Preserves the correct `qty` for valid items (regression guard for
  `updateQty` recovery: rebuild must carry the current quantity).
- [ ] Empty input for a merchant returns `[]` so the caller knows there is
  nothing to restore.

### Tests: `mergeCartSnapshot`

- [ ] Updates only items matching the `merchantKey`; leaves the others
  unchanged.
- [ ] `cartId`/`continueUrl`/`checkoutUrl` from the snapshot overwrite old
  values. If the snapshot field is missing (`undefined`), fall back to the
  original item value.
- [ ] `qty` comes from `line.quantity` in the snapshot; otherwise `item.qty`
  remains unchanged.
- [ ] `syncing`/`syncError` are always cleared after merge.
- [ ] `deliveryGroups`: null entries are filtered out. An empty snapshot keeps
  the previous groups instead of becoming `undefined`.

---

## Block 3 - Checkout Handoff URL (medium priority)

Helper `firstUrl` in `MeantApp.tsx` is private. Export it to `utils.ts`. It
drives the choice between `continueUrl` and `checkoutUrl` from the "use merchant
checkout handoff URL" change.

### Tests: `firstUrl`

- [ ] Returns the first non-empty value in argument order.
- [ ] Skips `null`, `undefined`, an empty string, and strings containing only
  whitespace.
- [ ] Trims the returned value.
- [ ] Everything empty -> `null`.

> If you decide to also test preference for `continueUrl` over `checkoutUrl` in
> the checkout flow, extract that selection logic from `checkout()` into a pure
> `resolveHandoffUrl(profile, payload)` function. This is optional. Flag it in
> the PR so we can decide whether the extract is worth it.

---

## Block 4 - Pure Logic in `utils.ts` (ongoing, lower priority)

`utils.ts` has 33 exported pure functions, and 3 are currently tested
(`cartGroups`, `cartDeliveryOptions`, `displayProductCategoryValue`). Add tests
for functions where a bug changes a shopping decision or money:

- [ ] **`bestOffer`** - selects the cheapest available offer for the given
  locations; ties; no available offer.
- [ ] **`bestCode`** - selects the best discount/gift-card code.
- [ ] **`productPriceFrom`** / **`productMerchantCount`** - aggregation across
  offers, including boundary cases such as 0 offers.
- [ ] **`canMerchantShip`** / **`productsForLocation`** - filtering by
  deliverability to a location.
- [ ] **`productsForPreferences`** - filtering by preferences, positive and
  negative polarity.
- [ ] **`productMatchesClothingFit`** / **`productsForClothingFit`** - men /
  women / other.
- [ ] **`computeSmartAlerts`** - alert generation such as better price, code,
  and similar cases.
- [ ] **`cartLines`** - builds cart lines from items.
- [ ] **`orderTotal`** - order total calculation. Because this is money, verify
  it does not accumulate floating-point error; prefer minor units.
- [ ] **`createOrder`** - maps `CheckoutPayload` to `Order`.
- [ ] **`deriveFilters`** / **`resolveAsk`** / **`resolveReply`** -
  deterministic text parsing into filters/replies.
- [ ] **`readStorage`/`writeStorage`** - round-trip and fallback when
  localStorage contains broken JSON; must not crash the app.
- [ ] **`money`** - formatting for currency, decimal places, and negative
  values.
- [ ] **`listJoin`** / **`prefLabel`** - small but cheap to cover.

---

## What Not To Test

- Rendering `MeantApp`, routes, or Supabase auth. Without RTL/jsdom, these are
  out of scope.
- Real HTTP calls to the backend.
- `schema.d.ts` (generated) and `routeTree.gen.ts` (generated).

## Acceptance Criteria

- `bun test` passes, including the existing `utils.test.ts`.
- `npm run typecheck` and `npm run build` remain clean; refactors must not break
  imports.
- No duplicated logic: after extraction, closures in `MeantApp` call exported
  functions instead of carrying their own copies.
- Every extract is a pure function without React state / `window` dependencies,
  except `readStorage`/`writeStorage`, where `localStorage` is mocked.
