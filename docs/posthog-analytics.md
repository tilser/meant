# PostHog product analytics

Meant forwards its existing semantic browser events to PostHog. Feature code should continue to
call `trackMeantEvent()` from `frontend/src/features/meant/analytics.ts`; it must not import or call
`posthog-js` directly. Backend analytics are not part of this integration.

## EU project and environment

Create the project in PostHog EU Cloud, then copy its browser project token and API host from the
project setup page. The `phc_...` browser token is public by design, but it must still be kept
separate from PostHog personal API keys and every other private credential.

Configure these variables only on the deployed production frontend:

```dotenv
VITE_POSTHOG_ENABLED=true
VITE_POSTHOG_PROJECT_TOKEN=phc_<public-browser-project-token>
VITE_POSTHOG_HOST=https://eu.i.posthog.com
VITE_POSTHOG_SESSION_REPLAY_ENABLED=false
VITE_POSTHOG_SESSION_REPLAY_SAMPLE_RATE=0.05
```

Initialization is a no-op unless the build is a Vite production build, analytics is explicitly
enabled, both the token and HTTPS host are present, and the page is not on localhost or a loopback
host. Private-network IPs and development-only hostnames such as `*.local` and `*.test` are also
rejected. Do not expose a production PostHog configuration to preview deployments; use a separate
project if preview analytics are ever needed.

The integration intentionally disables click autocapture, pageviews, pageleave, dead/rage clicks,
heatmaps, exception capture, surveys, performance capture, referrer/campaign capture, and session
replay by default. Meant's semantic events and stored campaign attribution are the source of truth.
This also avoids duplicate SPA pageviews when campaign parameters are removed with
`history.replaceState()`.

## Attribution and event funnel

Campaign deep links are captured before Meant removes `brief`, `utm_source`, `utm_medium`,
`utm_campaign`, and `utm_content` from the address bar. The brief is never sent to PostHog. The
session-scoped UTM values in `sessionStorage` are added to every later semantic event, so attribution
does not depend on PostHog seeing the original URL. Only bounded identifier-shaped campaign values
are forwarded; values shaped like email addresses, URLs, free text, or credentials are dropped.

The primary funnel is:

1. `campaign_brief_loaded` → `brief_submitted`
2. `agent_results_viewed` / `agent_refinement_submitted`
3. `product_opened`
4. `protected_action_attempted` → `auth_sheet_viewed` → `auth_method_selected`
5. `anonymous_account_converted` or `existing_account_signed_in`
6. `guest_conversation_imported` → `pending_action_completed` → `activated_account`
7. `merchant_outbound_clicked`

Supporting events include anonymous bootstrap success/failure, auth-sheet dismissal, new-conversation
gating, and pending-action failure. PostHog receives only bounded categorical values, booleans,
refinement ordinals, and the four UTM fields. Raw briefs/prompts, chat text, error text, conversation
IDs, email addresses, auth tokens, request/response bodies, headers, and checkout/payment details are
not forwarded.

When adding a semantic event, update the runtime event-name list, the property privacy allowlist in
`safeMeantEventProperties()`, and the focused forwarding test together. Adding only a TypeScript type
or call site will not make a new event/property eligible for forwarding.

## Identity lifecycle

PostHog creates and persists its own anonymous browser identity while Meant uses a Supabase anonymous
session. Meant never calls `identify()` with the Supabase anonymous UUID.

When Supabase resolves a permanent account, the integration calls `posthog.identify()` with the
stable canonical user UUID and no person properties. This joins earlier anonymous activity to that
profile while keeping email and profile fields out of analytics. Anonymous-to-permanent conversion
and guest-to-existing-account sign-in do not reset PostHog first, so the guest funnel and conversation
transfer remain intact.

A real Supabase `SIGNED_OUT` event resets PostHog before the next anonymous session is used. A direct
permanent account A → B change also resets before identifying B, preventing two permanent accounts
from being merged.

## Session replay and privacy

Meant currently has no analytics-consent UI. Replay must remain disabled until the product has an
approved consent/legal basis and the deployment's privacy notice reflects the use. Product analytics
respects the browser Do Not Track signal.

Replay requires both PostHog's **Record user sessions** project setting and
`VITE_POSTHOG_SESSION_REPLAY_ENABLED=true`, and runs only on the main `/` route. Enable both only
after the consent/legal requirement above is satisfied. Its client-side sample rate defaults to 5%
and is capped at 10%, even if a larger value is configured. When enabled, the SDK masks every input
and all text, blocks chat, auth, account,
inventory, preferences, orders, cart, checkout, and shelf regions, disables
canvas/cross-origin iframe/font capture, and strips network URLs while removing request/response
bodies and headers. A local sampling gate runs before recording can start, so dashboard trigger
groups cannot raise the deployment above the configured cap. Console and performance capture remain
off. The recorder is bundled in its own browser chunk and generic external PostHog extension loading
is disabled, preventing remote enablement of Logs, Conversations, tours, or surveys. Keep PostHog's
project-level network body/header recording disabled as defense in depth.

The CSP permits PostHog's current and lazy-loaded EU assets through `https://*.posthog.com`; HTTPS
ingestion was already allowed by `connect-src`. The wildcard follows PostHog's
[official CSP guidance](https://posthog.com/docs/advanced/content-security-policy), since asset
subdomains can change.

## Verification

Use a deployed non-local production build with the intended EU project configuration:

1. Open PostHog **Activity → Live events** and filter for `brief_submitted` or another semantic event.
2. Follow a URL with UTM parameters, submit the generated brief, and confirm later events carry the
   same `utm_source`, `utm_medium`, `utm_campaign`, and `utm_content` values but no `brief` property.
3. In browser DevTools **Network**, filter for `posthog` or `eu.i.posthog.com` and inspect the event
   request. Confirm the destination is the EU host and the payload contains no raw user content.
4. Append `?__posthog_debug=true` temporarily to enable the SDK's documented console diagnostics.
   Missing requests on localhost or in a development/test build are expected.
5. Exercise an anonymous action, complete registration/sign-in, and verify the events converge on
   the canonical user profile. Then log out and confirm a later browser event has a new anonymous
   distinct ID.

See PostHog's current documentation for
[JavaScript SDK configuration](https://posthog.com/docs/libraries/js#config),
[identification and reset](https://posthog.com/docs/product-analytics/identify), and
[session replay privacy](https://posthog.com/docs/session-replay/privacy).
