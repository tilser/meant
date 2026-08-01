# Meant exploratory QA report

Test date: 2026-08-01  
Target: `http://localhost:3000/`  
Account: provided David test account  
Scope: authenticated application, excluding checkout submission and checkout flows  
Viewports: desktop and 390 x 844 mobile; light and dark themes

## Executive summary

The core browsing experience is promising: authentication works, agent results are visually polished, product pagination works, saved items persist, preferences update recommendation suggestions, compare is useful, and the inventory filters work. The strongest screenshot candidate is `06-compare-landing.png`.

The highest-risk findings are around commerce data trust:

- The cart is lost on page reload even though the UI presents it as an account-scoped smart cart.
- The same product changes between `$36.69`, `€32.00`, and `$32.00` in different views while the account currency is USD.
- Cart totals briefly show impossible savings, delivery charges, and totals for roughly eight seconds after a quantity change.

Agent quality is useful but inconsistent. A specific search completed in 33.8 seconds, while another was still running after 113.6 seconds. Preferences affected suggested prompts and some lower-ranked matches, but top-result explanations did not reliably account for important preferences such as no polyester, sustainability, and strong reviews.

No JavaScript application warnings or errors appeared in the browser console during the pass. Checkout buttons were never activated.

## Findings

### QA-01 — P1 — Cart disappears after reload

Impact: A user can lose a prepared cart merely by reloading the page. This is a serious confidence and conversion issue.

Steps to reproduce:

1. Sign in.
2. Run a product search.
3. Add a product to the cart and wait until cart synchronization finishes.
4. Confirm the header cart badge and full-cart line item.
5. Reload the page.
6. Wait at least 11 seconds for account data to rehydrate.

Observed: The cart badge disappears and Smart cart reports an empty cart.

Expected: The account-scoped cart should be restored after reload, or the UI should clearly disclose that the cart is intentionally ephemeral.

Code pointers:

- `frontend/src/features/meant/cart/useCartController.ts:101` creates account-keyed session state.
- `frontend/src/features/meant/shared/storage.ts:57` contains browser-storage hydration and writes. Inspect initialization and auth-owner changes for a race that writes an empty state before the saved state is hydrated.

### QA-02 — P1 — Price and currency are inconsistent across product surfaces

Impact: Users cannot know which amount they will actually pay. This is particularly risky in cart and comparison contexts.

Steps to reproduce:

1. Set account currency to USD (it was already USD in this account).
2. Search for a black T-shirt under $60.
3. Open or save `Salthouse T-Shirt Black`.
4. Compare the amount in search, Saved/product detail, Compare, and Cart.

Observed for the same product:

- Search result: `$36.69`
- Saved/product detail: `€32.00`
- Cart: `$32.00`
- Compare: `€32.00`, alongside other products shown in dollars

The cart appears to keep the numeric merchant-native amount `32.00` while changing only the currency symbol to `$`.

Expected: Use one explicitly identified monetary authority and convert it consistently to the selected account currency, including currency code, rounding, and conversion timestamp. Never relabel an amount by swapping the symbol.

### QA-03 — P1 — Cart displays impossible totals while quantity syncs

Impact: The UI presents financially incorrect values long enough to be read and trusted.

Steps to reproduce:

1. Add the $24.99 Grunt Style T-shirt to the cart.
2. Open the full cart.
3. Increase quantity from 1 to 2.
4. Inspect the summary immediately, then again after synchronization.
5. Decrease quantity to 1 and repeat.

Observed:

- After increasing to 2: subtotal `$49.98`, savings `-$29.98`, delivery `$4.99`, total `$24.99`.
- After decreasing to 1: line item/subtotal `$24.99`, but delivery `$24.99` and total `$49.98`.
- Values corrected after approximately 7.6–8.0 seconds.

Expected: Keep the last confirmed merchant breakdown while syncing, or show placeholders/skeletons. Do not combine a new quantity with stale snapshot totals.

Evidence: `04-cart-sync-pricing-bug.png`

Code pointers:

- `frontend/src/features/meant/cart/CartView.tsx:278` combines snapshot and fallback totals.
- `frontend/src/features/meant/utils.ts:779` injects fallback delivery (`subtotal >= 50 ? 0 : 4.99`). This fallback can conflict with an older merchant snapshot during synchronization.

### QA-04 — P2 — Direct add-to-cart silently chooses size 3XLarge

Impact: A user can accidentally order an unsuitable variant.

Steps to reproduce:

1. Search for an everyday T-shirt.
2. On the `Grunt Style Basic T-Shirt White` result card, click Add to cart without opening product details.
3. Open the full cart.

Observed: The chosen variant is `3XLarge`. The account's Clothing fit preference is `No preference`; the only saved numeric size was for soccer shoes.

Expected: Ask for a required variant before adding, or apply a clear, explainable saved clothing-size preference. Do not silently choose an extreme/default variant.

### QA-05 — P2 — Agent search can exceed 113 seconds; Stop has ambiguous semantics

Impact: A stalled request feels broken, and the Stop action does not clearly communicate whether generation was cancelled or completed.

Steps to reproduce:

1. Submit: `Find me a comfortable everyday T-shirt under $50. Explain how each recommendation fits my preferences.`
2. Observe the status and elapsed time.
3. Click Stop after the long-running state.

Observed timeline:

- 4.2 s: `Filtering out…`
- 17.3 s: still searching
- 32.0 s: `Almost found`
- 54.3 s and 87.8 s: still running
- 106.3 s: still running (`01-agent-search-stuck.png`)
- 113.6 s: Stop clicked
- Stop reacted in 342 ms, but disabled results appeared under `Finishing…`; the view became usable about nine seconds later.

A second, simpler query rendered cards at 14.9 seconds and completed at 33.8 seconds.

Expected: Establish a latency budget, stream meaningful intermediate progress, and surface a clear terminal state: Cancelled, Partial results, or Completed. If partial results are retained after Stop, say so explicitly.

### QA-06 — P2 — Preference use is only partial and explanations overclaim personalization

Impact: The agent says results are ranked by the user's preferences, but the evidence shown does not support all important preferences.

Test:

1. Added a temporary `Streetwear` preference.
2. Submitted: `Find me a black T-shirt under $60. Tell me why the top result is meant for me.`
3. Reviewed result order, top explanation, tags, review information, and conflicts.

What worked:

- Home suggestion prompts changed to include Streetwear.
- A Streetwear-tagged Scuffers product appeared on page 2 at rank 8.
- The response respected the black-color and budget constraints and provided merchant/price information.

Observed weakness:

- The top result explanation omitted the new Streetwear preference.
- It did not account for `No polyester`, `Sustainable`, or `Strong reviews`.
- The top item was described as a cotton blend without disclosing whether the blend contained polyester.
- All first-page cards said `No review data`, yet the answer did not flag the conflict with `Strong reviews`.
- Some high-ranked cards used generic rationale rather than product-specific preference evidence.

Expected: For each recommendation, list matched preferences, conflicts, unknowns, and hard constraints separately. Avoid phrases such as “no trade-offs” when material composition or review evidence is unknown.

### QA-07 — P2 — Shelf discards known price and store count

Impact: A useful product becomes less informative when moved to the shelf.

Steps to reproduce:

1. Find the Scuffers product showing `$44.94` and one store.
2. Click Set aside / add to shelf.
3. Open the shelf.

Observed: Shelf shows `Price unavailable` and `current store count unavailable`, even though the source result had both facts.

Expected: Display the stored snapshot while live rehydration runs, label it `Last checked …`, then update when authoritative data arrives.

Code pointer: `frontend/src/features/meant/shelf/Shelf.tsx:115` sets product facts to null when `authoritative` is false instead of falling back to the stored snapshot.

### QA-08 — P2 — Product facts flicker and disagree during rehydration

Impact: Plausible data disappears and reappears as different data without a loading or provenance explanation.

Steps to reproduce:

1. Save `Salthouse T-Shirt Black`.
2. Sign out and sign back in.
3. Open Saved, then product detail.
4. Observe price, stores, reviews, images, and stock for several seconds.

Observed:

- Saved initially showed `€32.00` and sometimes `No review data`.
- Opening detail temporarily cleared to `Price unavailable`, `0 stores`, `No review data`, and `Checking stock…`.
- About 2.5 seconds later it rehydrated to `€32.00`, one store, 5.0/17 reviews, six images, and variant stock.
- Search and Compare still displayed different monetary/review facts.

Expected: Keep stable snapshot data visible with a revalidating indicator, and identify the authoritative source. Update related surfaces from the same normalized product state.

### QA-09 — P2 — Pin and Watch actions provide no visible outcome

Impact: Users cannot tell whether the action succeeded, failed, or is unavailable.

Steps to reproduce:

1. Open a historical product-search chat.
2. Click Watch on a result.
3. Click Pin on the same result.
4. Wait and inspect the card, shelf/trays, and conversation.

Observed: No button state, toast, system message, tray item, or error appeared.

Expected: Disable actions that are not implemented and label them Coming soon, or provide an optimistic state plus success/failure feedback.

Code pointers:

- `frontend/src/features/meant/agent/AgentDiscoverView.tsx:896` initializes `watchedSet` as always empty.
- `frontend/src/features/meant/agent/AgentDiscoverView.tsx:1377` treats Watch as a coming-soon message.
- `frontend/src/features/meant/agent/AgentDiscoverView.tsx:1350` invokes the Pin action without a visible state transition in this flow.

### QA-10 — P3 — SPA navigation preserves scroll and hides the Compare heading

Steps to reproduce:

1. Scroll down in a long agent-results conversation.
2. Navigate to Compare using the application navigation.

Observed: Compare opened at approximately `scrollY = 199.5`; its heading was hidden beneath the sticky header.

Expected: Reset scroll to the top on primary-route navigation, except for intentional back-navigation restoration.

### QA-11 — P3 — Mobile chip rows and result semantics need responsive polish

At 390 x 844:

- Preference and suggestion-chip rows are horizontally clipped; `Edit` is squeezed and there is no clear scrolling affordance.
- The search placeholder is clipped.
- The product carousel visually displays one result, but all 20 result cards remain exposed in the DOM/accessibility tree. Off-screen cards should be virtualized or marked appropriately (for example, `aria-hidden`).
- An already-open Shelf covers almost the entire mobile screen; it is closable, but the transition/state is abrupt.

Evidence: `09-mobile-results.png`, `10-mobile-home.png`

### QA-12 — P3 — Dark mode logo contrast and localized native file input are inconsistent

Steps to reproduce:

1. Enable dark mode.
2. Open Inventory and the Add item form.

Observed:

- The dark-blue Meant logo has very low contrast against the black header.
- The native file input displays Czech browser strings (`Vybrat soubor`, `Soubor nevybrán`) while the rest of the app is English.

Expected: Supply a light/dark logo treatment. If the app is English-only, use a styled upload control with app-owned copy; if localized, localize the whole workflow consistently.

Evidence: `07-dark-inventory.png`

### QA-13 — P3 — Share dialog has ambiguous/non-working close affordance

Steps to reproduce:

1. Open a chat from History.
2. Click Share.
3. Click the first Close control.

Observed: The dialog remained open; a second Close control dismissed it. The dialog exposes a placeholder `app.meant.com/s/coming_soon` link.

Expected: One consistent close action, and Coming soon should be an explicit disabled/product state rather than a copyable pseudo-link.

### QA-14 — P3 — Preference summary silently hides preferences

Observed: The profile contained seven preferences (and temporarily eight), but the header summary showed only six chips with no `+1`/`+2` indicator.

Expected: Make truncation explicit and provide a direct way to inspect the hidden preferences.

### QA-15 — P3 — Cart count semantics differ between surfaces

Observed: A single line item at quantity 2 produced a header badge of `2`, while full cart said `1 items` and the merchant group said `1 item`.

Expected: Label counts consistently as either `2 units` and `1 product`, or use one definition everywhere. Also fix the `1 items` pluralization.

## Performance observations

These are wall-clock UI observations, not synthetic benchmarks:

| Operation | Observed time |
|---|---:|
| Add temporary preference reflected in profile | 306 ms |
| Add-to-cart badge update | 298 ms |
| Add-to-cart merchant synchronization | 7.6–9.6 s |
| Remove-from-cart response | about 300 ms |
| Quantity sync corrected totals | 7.6–8.0 s |
| Agent query 1 | still running at 113.6 s; stopped |
| Agent query 2 | cards at 14.9 s; answer usable at 33.8 s |
| Sign-out to login screen | about 8.5 s |
| Sign-in account data fully restored | about 8.5 s |
| Open recent history chat | under 1.85 s |
| Product detail live rehydration | about 2.5 s |

Recommendations:

- Add OpenTelemetry/server-timing spans for agent planning, catalog search, ranking, explanation generation, and final serialization.
- Show a visible distinction between local optimistic UI and merchant-confirmed state.
- Record cancellation reason and partial-result status in the chat event model.
- Put performance budgets around sign-in hydration, cart sync, and first usable agent results.

## What was tested

- Application reachability and authenticated home page
- Sign out and sign back in with the provided account
- Two natural-language product searches
- Agent progress states, Stop, partial/final response behavior, result rationale, and follow-up copy
- Product-result pagination on desktop and mobile carousel behavior
- Product details, image/variant inventory, merchant count, price, and review rehydration
- Add to cart from a result card and from product detail
- Full-cart quantity increase/decrease, totals, merchant grouping, badge behavior, and removal
- Cart persistence across page reload
- Saved-item add/remove, reload persistence, and persistence through sign-out/sign-in
- Compare add/remove of a saved item and preference comparison
- Preference add/remove, persistence, generated suggestion changes, ranking influence, and explanation quality
- History count, recent-chat opening, message counts, pagination, and Share dialog
- Shelf set-aside/open/remove behavior and dusting transition
- Pin and Watch controls
- Inventory loading, counts, category filter, restock-only empty state, refresh, and add-item form (no item submitted because a photo file is mandatory)
- Account settings display, selected USD currency, newsletter state, and connected-store empty state
- Light and dark themes
- Desktop and 390 x 844 mobile layouts
- Browser console warnings/errors throughout the pass

Not tested by design:

- Checkout buttons, checkout initialization, payment, order placement, or any downstream checkout flow
- Destructive history deletion
- Connected-store authorization (the test account had no connected store)
- Inventory item creation (the form requires a local photo and none was supplied)

## Console and server logs

Browser-console result: no application warnings or errors. The captured normal messages are in `browser-console.log`; they contain Vite connection events and React DevTools informational output only.

Limitations:

- The controlled page did not expose the browser Performance API, so network-resource timing and Core Web Vitals could not be collected from the page.
- No application terminal was attached to this Codex task.
- Process inspection was blocked by the sandbox, and no repository `.log` files were present. Therefore backend request/trace logs are not included.

For the next run, start the backend with request IDs and structured logs enabled, then correlate the slow agent search and cart mutations by conversation/cart ID.

## Screenshots

- `01-agent-search-stuck.png` — evidence of long-running agent state at 106 seconds
- `02-recommendations.png` — desktop recommendation results
- `03-full-cart.png` — full-cart layout
- `04-cart-sync-pricing-bug.png` — incorrect transient cart totals
- `05-compare.png` — preserved-scroll issue
- `06-compare-landing.png` — strongest landing-page candidate
- `07-dark-inventory.png` — dark-mode and file-input evidence
- `08-mobile-chat.png` — shelf covering mobile chat
- `09-mobile-results.png` — mobile result carousel
- `10-mobile-home.png` — mobile home and clipped chip rows

## Test-data cleanup and residual state

Cleaned up:

- Temporary `Streetwear` preference removed
- Test saved product removed
- Cart emptied
- Shelf emptied
- Compare restored to its original two items
- Inventory filters restored to All
- Theme and viewport restored to light desktop defaults

Residual changes caused by testing:

- Two QA search conversations remain in History (chat count increased from 22 to 24).
- The learned Natural materials behavior boost increased from `+2.8` to `+4.2` as a result of product interactions.

