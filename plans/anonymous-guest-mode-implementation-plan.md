# Anonymous Guest Mode and Contextual Account Conversion

## Objective

Remove the mandatory authentication screen from the main Meant application entry point.

Anyone opening `https://app.usemeant.com` without a permanent account must automatically receive an anonymous Supabase session and enter Discover. Account creation or sign-in is requested only when the user performs an action that requires durable state or a permanent identity.

The target flow is:

```text
Open app.usemeant.com
  -> create or restore anonymous session
  -> use Discover and receive real results
  -> refine the active conversation as often as needed
  -> attempt a durable/account-only action
  -> open a contextual authentication sheet
  -> create or enter a permanent account
  -> return to the same UI state
  -> complete the original action exactly once
```

Supabase supports this model through anonymous sign-in followed by identity linking. The implementation should follow the current official guidance:

- <https://supabase.com/docs/guides/auth/auth-anonymous>
- <https://supabase.com/docs/guides/auth/auth-identity-linking>

## Product Decisions

### Main application entry

- `app.usemeant.com/` must not show a login or sign-up page.
- If a permanent session exists, open the full application.
- If an anonymous session exists, open the application in guest mode.
- If no session exists, create an anonymous Supabase session in the background and then open Discover.
- Show a neutral loading state while the initial session is being resolved.
- If anonymous sign-in fails, show a retry state rather than the current full-screen login page.
- Creating the anonymous session must be idempotent. React remounts, retries, or concurrent session listeners must not create multiple anonymous users.

### Explicit login route

Keep authentication available on a separate route:

```text
/login
```

This route is intended for:

- returning users on a new device;
- users who explicitly choose to sign in before using the product;
- password recovery;
- OAuth recovery and callback handling;
- `Already have an account?` links.

Opening `/login` must not first create an anonymous user. Authentication callback routes must also avoid creating an unrelated anonymous session while an OAuth or email flow is being completed.

### Guest terminology

Do not present anonymous authentication as a choice and do not show copy such as:

- `Continue anonymously`;
- `Guest account`;
- `Anonymous mode`;
- `Create an account to use Meant`.

The anonymous account is an implementation detail. The user should simply enter and use Meant.

For an anonymous user, the account area in the application header should offer:

> Save your progress

A secondary `Sign in` action may be available for existing users.

### Unlimited refinement of the active conversation

An anonymous user may continue refining the current active conversation as many times as they want.

- Do not show an authentication gate based on the number of follow-up messages.
- Do not implement a product rule such as “one search plus one refinement.”
- Do not interpret topic changes inside the active conversation in order to force authentication.
- Normal platform-wide safety, abuse, concurrency, and fair-use limits still apply, but they must not be presented as a registration mechanic.
- Creating a separate new conversation or opening durable conversation history may require a permanent account.

## Guest Capabilities

### Anonymous users may

- Open Discover.
- Submit an initial shopping request.
- Continue refining the same active conversation without a registration gate.
- Use a campaign brief supplied in the URL.
- See the real product results returned by the agent.
- See match scores, matching and conflicting preferences, trade-offs, current prices, offers, and merchant identities when available.
- Open product details.
- Change a product variant or offer before any cart is created.
- Compare up to two products within the current guest session.
- Follow a normal outbound merchant link.
- Switch the visual theme.
- Reload the application and recover the anonymous session and active conversation while the anonymous Supabase session still exists.
- Cancel an active agent run.

### A permanent account is required for

- Saving a product.
- Opening or using Saved.
- Saving multiple conversations or opening durable conversation history.
- Starting a separate new conversation.
- Persisting shopping preferences, sizes, locations, or other profile data across devices.
- Comparing more than two products.
- Inventory.
- Price or stock alerts.
- Creating or mutating a Meant-managed cart.
- Embedded checkout or direct checkout completion.
- Orders.
- Newsletter subscription.
- Profile name or profile-picture changes.
- Durable shared shortlists or voting when those features are implemented.

These restrictions must be enforced by the backend. Hiding controls in the frontend is not sufficient.

### Do not gate the first value

Do not require a permanent account before:

- the first real agent response;
- the product evidence and recommendation explanation;
- opening product details;
- viewing price, offer, availability, or merchant information;
- following a standard outbound merchant link.

Do not blur or hide critical product facts as a registration tactic. Account conversion should be exchanged for persistence, continuity, and account-owned actions rather than access to the basic answer.

## Guest Discover Experience

### Initial profile state

- Do not display a fake personal name for a new guest.
- Do not claim that Meant already knows a guest's preferences before the guest has provided any signal.
- Use neutral introductory copy until the first request establishes relevant constraints.
- Existing permanent-account behavior and saved preferences must remain unchanged.
- Query-derived preferences may be displayed as provisional suggestions, but they must not be permanently saved without explicit confirmation after account conversion.

Suggested initial copy:

> Tell Meant what you are looking for and what you will not compromise on.

### Provisional preference prompt

After the agent has identified meaningful constraints, show a non-blocking prompt such as:

> Meant noticed what matters to you

```text
No polyester | Wool | Under $150
```

CTA:

> Remember these

Clicking the CTA opens the contextual authentication sheet. The preferences are persisted only after permanent account creation and explicit confirmation.

## Campaign Deep Links

The application root must accept campaign parameters such as:

```text
https://app.usemeant.com/
  ?brief=Find+me+a+wool+coat+under+150+without+polyester
  &utm_source=tiktok
  &utm_medium=creator
  &utm_campaign=natural_fibers
  &utm_content=wool_coat_01
```

Requirements:

- `brief` starts a Discover search automatically once the chat is ready.
- Automatic submission is claimed once so refreshes and view changes do not repeat the search.
- UTM and creator attribution are captured for the anonymous session.
- Attribution survives permanent-account conversion.
- Campaign parameters must not become user preferences or identity metadata.
- After capture, tracking parameters may be removed from the visible URL using history replacement.
- Refreshing the page must not submit the brief or duplicate an analytics event.
- Invalid, excessively long, or malformed brief values must be rejected or safely truncated at the frontend boundary.

## Contextual Authentication Sheet

### Architecture

Split the responsibilities currently owned by `frontend/src/features/meant/auth/AuthScreen.tsx` into:

- an explicit `/login` page;
- a reusable `AuthSheet` component;
- authentication services/hooks independent of a specific screen;
- pending-action state that survives OAuth redirects.

The sheet opens when:

1. the guest clicks `Save your progress`;
2. the guest attempts an account-only action;
3. the backend returns `PERMANENT_ACCOUNT_REQUIRED`;
4. the guest attempts to start another separate conversation.

The sheet must not open automatically because of time on page, scroll position, product-detail opens, or follow-up count.

### Generic copy

> **Take Meant with you**  
> Keep your searches, shortlist and shopping preferences on every device.

Actions:

- Continue with Google
- Continue with Apple
- Continue with email
- Already use Meant? Sign in

Supporting copy:

> Free. No card. You will continue exactly where you left off.

### Contextual variants

#### Save product

> **Keep this product**  
> Save this product and the preferences behind it so it does not disappear.

#### Remember preferences

> **Let Meant remember this**  
> Keep these rules for every future search.

#### Start a new conversation

> **Keep your shopping conversations together**  
> Save this conversation and start a new one without losing what Meant already found.

#### Create cart

> **Keep your cart safe**  
> Sign in before Meant creates and coordinates your merchant carts.

### Interaction requirements

- The sheet can be dismissed without losing the guest conversation.
- Authentication errors are displayed inside the sheet.
- Do not require a full name during conversion.
- Prefer Google, Apple, and passwordless email/magic-link or OTP flows.
- Do not force a new user to choose between separate sign-in and sign-up concepts before selecting an identity method.
- Provide an explicit route for a user who already has a Meant account.
- The sheet must work on mobile and inside the TikTok in-app browser, with a graceful external-browser fallback if an OAuth provider blocks embedded-browser authentication.

## Pending Account Actions

Before opening the authentication sheet, capture the user's intended action.

Suggested frontend model:

```ts
type PendingAccountAction =
  | { type: 'SAVE_PRODUCT'; productId: string }
  | { type: 'REMEMBER_PREFERENCES'; preferenceDraftId: string }
  | { type: 'START_NEW_CONVERSATION'; initialQuery?: string }
  | { type: 'OPEN_SAVED' }
  | { type: 'OPEN_HISTORY' }
  | { type: 'OPEN_INVENTORY' }
  | { type: 'ADD_TO_CART'; productId: string; offerKey: string }
  | { type: 'ENABLE_ALERT'; productId: string }
```

After successful account conversion or sign-in:

1. Restore the original primary view.
2. Restore the active conversation, product modal, and relevant scroll context.
3. Execute the pending action automatically.
4. Show success or actionable failure feedback.
5. Remove the pending action.
6. Never execute the pending action twice after reload, callback replay, or auth-state duplication.

Pending actions must not contain access tokens, refresh tokens, complete payment data, or unnecessarily large product payloads. Prefer stable IDs and server-owned state. Keep redirect metadata in session storage or a server-issued opaque state value rather than exposing it in query parameters.

## Frontend Authentication Changes

### `useSupabaseAuth`

Extend `frontend/src/features/meant/auth/useSupabaseAuth.ts` with:

- `signInAnonymously()`;
- `ensureSession()`;
- `isAnonymous`;
- `linkOAuthIdentity(provider)`;
- passwordless email conversion;
- explicit existing-account sign-in;
- anonymous bootstrap status;
- an idempotency guard for anonymous creation;
- callback and recovery-state handling;
- a safe way to preserve and resume the pending account action.

The hook must distinguish:

```text
initializing
anonymous
permanent
signed-out explicit login
auth callback/recovery
error
```

### Application initialization

Update `frontend/src/features/meant/MeantApp.tsx` so that it does not render `AuthScreen` whenever the session is absent.

For an anonymous session:

- initialize only guest-allowed data;
- do not eagerly call protected Saved, Inventory, Orders, Newsletter, profile-picture, cart, or durable-settings mutations;
- do not turn expected guest restrictions into global application errors;
- preserve the current agent conversation and result state;
- show account-only navigation actions as conversion entry points rather than broken or silently disabled controls.

### Profile and account UI

- Frontend user/profile types must allow `email: null` for anonymous identities.
- The account screen itself requires a permanent account.
- Guest UI must not render blank email, fake initials, fake name, or the default `Meant` identity as if it belonged to the user.
- After conversion, refresh the user profile and account-owned state without resetting the active Discover UI.

## Supabase Configuration

Enable and validate in local, staging, and production environments:

- Anonymous Sign-Ins.
- Manual Identity Linking.
- Google OAuth.
- Apple OAuth.
- Passwordless email if included in the first release.
- Correct redirect URLs for local, preview, staging, and production environments.
- CAPTCHA/Turnstile support for anonymous sign-in.

Document the required settings and environment variables in `docs/deployment.md` and `frontend/.env.example` when an environment variable is required.

## New-Identity Conversion

When the selected Google, Apple, or email identity does not yet belong to a Meant account:

- link the identity to the current anonymous Supabase user;
- keep the same Supabase/user UUID;
- keep the active conversation, runs, artifacts, provisional preferences, and campaign attribution;
- mark the session as permanent without redirecting to an empty application state;
- refresh the local Meant user profile from the new JWT claims;
- resume the pending action exactly once.

## Existing-Account Sign-In

When the selected identity already belongs to a permanent Meant account:

- sign in to the existing account;
- preserve the active guest result during the current browser flow;
- transfer the active guest conversation, or at minimum its current user-visible search result and grounded product artifacts, through a backend-owned transfer operation;
- execute the pending action under the permanent account;
- never allow the frontend to reassign arbitrary `user_id` values.

### MVP merge policy

- Permanent profile data wins.
- Permanent settings and accepted preferences win.
- Provisional guest preferences are shown as suggestions requiring confirmation.
- Only the active guest conversation is imported; old guest conversations are not imported.
- Guest Saved, Inventory, Orders, Checkout, and durable cart state do not exist because those operations are blocked before conversion.
- Active agent runs must be completed or safely cancelled before ownership transfer.
- Transfer authorization must be one-time, short-lived, and bound to both the guest identity and the target permanent session.
- A replayed or expired transfer must fail without duplicating conversation data.

The existing-account flow is the highest-risk part of this feature and should be implemented and tested separately from basic anonymous bootstrap.

## Backend Identity Changes

### `AuthenticatedUser`

Update `backend/src/main/java/com/meant/api/module/user/service/dto/AuthenticatedUser.java` to carry anonymous identity state:

```java
public record AuthenticatedUser(
        UUID id,
        String email,
        String firstName,
        String surname,
        boolean anonymous
) {
}
```

Requirements:

- Read `anonymous` from the Supabase JWT `is_anonymous` claim.
- Treat a missing claim as `false` for existing permanent tokens unless Supabase guarantees a different explicit value.
- Allow `email` to be null for anonymous identities.
- Missing email or name claims must not fail JWT identity parsing.

### Local user profile

The current local user model requires an email, which is incompatible with Supabase anonymous identities.

Add a Liquibase XML changelog that:

- makes `users.email` nullable;
- retains uniqueness for non-null permanent emails;
- does not invent placeholder addresses such as `<uuid>@anonymous.local`.

Update:

- `backend/src/main/java/com/meant/api/module/user/service/command/EnsureUserProfileCommand.java`;
- `backend/src/main/java/com/meant/api/module/user/entity/User.java`;
- `backend/src/main/java/com/meant/api/module/user/service/UserService.java`;
- user repository insert/upsert code;
- user controller response DTOs and Swagger schemas;
- generated frontend API types and handwritten types that currently require a non-null email.

Specific behavior:

- Remove the unconditional `@NotNull` email requirement.
- Validate email format only when an email is present.
- Use null-safe email equality checks.
- Allow an anonymous profile row to be provisioned with a null email.
- Populate the email when the same UUID is converted into a new permanent identity.
- Do not overwrite user-edited names during identity refresh.

## Permanent-Account Policy

Introduce one consistent backend policy for account-only operations, for example:

```java
requirePermanentAccount(AuthenticatedUser user);
```

An anonymous user attempting an account-only operation receives an RFC 7807 response:

```json
{
  "status": 403,
  "code": "PERMANENT_ACCOUNT_REQUIRED",
  "title": "Permanent account required",
  "detail": "Save your progress to use this feature."
}
```

Apply the policy at least to:

- Saved mutations and reads;
- durable conversation history and new-conversation creation where required by the product rule;
- Inventory;
- Newsletter;
- profile edits and profile picture;
- Orders;
- Cart mutations;
- Checkout;
- durable settings, accepted taste signals, saved sizes, and locations;
- future alert and durable sharing operations.

Keep guest access to the agent conversation and run operations needed for the single active guest conversation.

Do not put business-domain guest rules into plugin packages. Authentication and shared identity parsing belong in application-wide security support; module-specific restrictions remain in their owning modules.

## Agent Usage and Abuse Controls

Do not add a registration gate based on the number of refinements in the active guest conversation.

The existing agent message and expensive-endpoint limits may continue to protect the system, but guest handling must satisfy these rules:

- The user may refine the active conversation repeatedly.
- Hitting a normal safety or fair-use limit returns the existing limit behavior, not a misleading `create an account to continue` response unless permanent accounts genuinely receive a different configured allowance.
- Do not configure `maximum-per-anonymous-user-per-day: 2` as part of this feature.
- Starting a separate conversation may trigger `PERMANENT_ACCOUNT_REQUIRED`.
- Rate limits must apply server-side and must not depend only on frontend state.

Before production enablement, add or confirm:

- Cloudflare Turnstile or equivalent CAPTCHA for anonymous account creation;
- per-user rate limiting using the anonymous UUID;
- IP-based protection for anonymous-account creation and expensive agent operations;
- concurrency limits for active agent runs;
- protection against repeated anonymous account creation;
- metrics for anonymous creation, conversion, abuse rejection, and model usage;
- cleanup of abandoned anonymous identities and their application data after an agreed retention period, for example 30 days.

Cleanup must never delete a user who has since become permanent.

## Post-Conversion Preference Flow

Do not show a generic multi-step onboarding flow immediately after conversion.

If the active conversation produced meaningful provisional preferences, show:

> **Use these as your defaults?**

Example:

- No polyester
- Natural materials
- Under $150

Requirements:

- Save the preferences only after explicit confirmation.
- Never silently overwrite an existing account's preferences.
- Ask for location, size, checkout details, or newsletter consent only when contextually relevant.
- Do not subscribe the user to marketing automatically.

## Analytics

Implement or route the following events through the selected product analytics system:

```text
anonymous_session_created
anonymous_session_failed
campaign_brief_loaded
brief_submitted
agent_results_viewed
agent_refinement_submitted
product_opened
protected_action_attempted
auth_sheet_viewed
auth_sheet_dismissed
auth_method_selected
anonymous_account_converted
existing_account_signed_in
guest_conversation_imported
pending_action_completed
pending_action_failed
new_conversation_gate_viewed
merchant_outbound_clicked
activated_account
```

Relevant event properties include:

- `anonymous`;
- `reason`;
- `auth_method`;
- `utm_source`;
- `utm_medium`;
- `utm_campaign`;
- `utm_content`;
- `conversation_id`;
- `pending_action_type`;
- `refinement_ordinal`;
- `identity_link_result`.

`activated_account` occurs only when permanent authentication succeeds and the pending action completes. Creating or entering an account alone is not activation.

Do not send raw shopping queries, inferred sensitive preferences, email addresses, access tokens, or product payloads to analytics unless an explicit privacy decision has approved that data.

## Frontend Tests

Add tests covering at least:

- Root without a session creates exactly one anonymous session.
- `/login` does not automatically create an anonymous session.
- OAuth/recovery callback handling does not create an unrelated guest.
- An existing permanent session is never replaced by a guest session.
- Anonymous bootstrap failure shows a retry state.
- A guest can submit a first request and repeatedly refine the active conversation.
- Refinement count alone never opens the authentication sheet.
- A guest can open product details and inspect offers.
- Save opens the correct contextual authentication sheet.
- Saved, History, New conversation, Inventory, Cart, Checkout, and Orders open the correct gate.
- The backend `PERMANENT_ACCOUNT_REQUIRED` code opens the corresponding sheet.
- Dismissing the sheet preserves the active guest conversation.
- Pending actions survive an OAuth redirect.
- A pending action executes exactly once after conversion.
- OAuth failure does not destroy the guest conversation.
- Campaign brief is pre-filled but not automatically submitted.
- UTM attribution survives conversion.
- Guest UI does not render fake profile data.
- Signing out of a permanent account and returning to root creates a new guest session without reopening the legacy login wall.
- Mobile bottom-sheet behavior and focus trapping remain accessible.

## Backend Tests

Add tests covering at least:

- An anonymous Supabase JWT is accepted as an authenticated request.
- A JWT without email provisions an anonymous local user profile.
- Repeated anonymous profile provisioning is idempotent.
- Converting the same UUID fills in the permanent email without losing guest data.
- A guest can create, read, refine, stream, and cancel only their own active agent conversation/run.
- Repeated refinement is not rejected by a guest-specific two-message rule.
- A guest cannot access another guest's or permanent user's data.
- Account-only operations return `PERMANENT_ACCOUNT_REQUIRED`.
- Permanent-account behavior remains unchanged.
- Existing system rate limits still apply correctly to anonymous UUIDs.
- A transfer token is one-time, short-lived, and bound to the guest user.
- An existing account cannot import another user's guest conversation.
- Replaying a transfer cannot duplicate a conversation or pending action.
- The nullable-email migration succeeds with existing production-shaped data.
- OpenAPI and frontend schema generation reflect nullable email and anonymous state.

## Acceptance Criteria

The feature is complete when all of the following are true:

1. A new visitor to `app.usemeant.com` never sees the mandatory login screen.
2. A new visitor can use Discover without providing PII.
3. A guest can receive real product results and refine the active conversation as often as needed.
4. Refinement count never triggers an authentication gate.
5. Refresh restores the anonymous session and current conversation while the guest session remains valid.
6. Account-only actions open a contextual authentication sheet.
7. The authentication sheet supports Google, Apple, and the approved email flow.
8. Dismissing or failing authentication does not destroy guest state.
9. A new identity converts the anonymous account without changing its user UUID.
10. Signing in to an existing account preserves the current user-visible result and pending action.
11. After authentication, the user returns to the same context.
12. The pending action completes automatically and exactly once.
13. Backend authorization enforces guest restrictions independently of the frontend.
14. Starting another separate conversation can require a permanent account, while the current conversation remains unrestricted.
15. Production anonymous sign-in is protected by CAPTCHA, rate limiting, and cleanup.
16. TikTok and other campaign links can pre-fill a brief and preserve attribution.
17. Explicit `/login` remains available for returning users.
18. Existing permanent login, Saved, Preferences, Inventory, Cart, Checkout, and Orders behavior is not regressed.
19. API documentation and generated frontend types match the new nullable-email and anonymous-user contract.

## Out of Scope

- Changes to the separate `usemeant.com` landing-page repository, except coordinating final app URLs and campaign parameters.
- Native mobile application work.
- Anonymous Meant cart or anonymous embedded checkout.
- A paid subscription or payment paywall.
- Public share pages and voting, unless implemented in a separate feature.
- Importing every abandoned guest conversation into an existing permanent account.
- Replacing the current commerce agent architecture.

## Recommended Delivery Sequence

### PR 1: Backend identity and anonymous profile support

- Parse `is_anonymous`.
- Make local user email nullable.
- Provision anonymous profiles.
- Update controller responses, OpenAPI, and generated frontend contracts.

### PR 2: Frontend anonymous bootstrap and explicit login route

- Add idempotent anonymous sign-in.
- Remove the mandatory auth wall from root.
- Add `/login` and callback/recovery handling.
- Add neutral guest UI and skip protected initialization calls.

### PR 3: Permanent-account policy

- Add `PERMANENT_ACCOUNT_REQUIRED`.
- Protect account-only backend operations.
- Convert account-only navigation into frontend auth-sheet entry points.
- Keep active guest conversation refinement unrestricted.

### PR 4: Authentication sheet and pending actions

- Add contextual sheet variants.
- Persist OAuth return context safely.
- Resume each pending action exactly once.

### PR 5: Account conversion and existing-account import

- Link new identities while preserving UUID.
- Implement the one-time guest transfer flow for an existing account.
- Confirm merge and conflict rules.

### PR 6: Campaign links, analytics, abuse protection, and end-to-end QA

- Add `brief` and UTM handling.
- Add analytics events.
- Add CAPTCHA, rate limits, metrics, and cleanup.
- Test mobile browsers, TikTok in-app browser, OAuth providers, refresh, and callback recovery.

The existing-account transfer flow is the highest-risk part of the feature and should not be combined with the initial anonymous-session bootstrap in a single pull request.
