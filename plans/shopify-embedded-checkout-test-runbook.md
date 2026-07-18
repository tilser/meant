# Shopify Embedded Checkout Test Runbook

This is the protected live journey for PCOS-016. Embedded checkout remains disabled by default in both backend and frontend configuration. Use Shopify development credentials and a development shop only.

## Required environment

Backend:

```dotenv
SHOPIFY_AGENT_AUTH_ENABLED=true
SHOPIFY_CLIENT_ID=<development-client-id>
SHOPIFY_CLIENT_SECRET=<development-client-secret>
SHOPIFY_AUTHORIZATION_TIER=TOKEN

SHOPIFY_GLOBAL_CATALOG_DISCOVERY_ENABLED=true
COMMERCE_CATALOG_ROLLOUT_ENABLED=true
COMMERCE_CART_ROLLOUT_ENABLED=true
COMMERCE_CHECKOUT_SESSION_ROLLOUT_ENABLED=true
COMMERCE_EMBEDDED_CHECKOUT_ROLLOUT_ENABLED=true
SHOPIFY_EMBEDDED_CHECKOUT_ADVERTISED=true
SHOPIFY_EMBEDDED_CHECKOUT_AUTHORIZED=true

APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Frontend:

```dotenv
VITE_MEANT_API_URL=http://localhost:8080
VITE_EMBEDDED_CHECKOUT_ENABLED=true
VITE_CHECKOUT_KIT_DEBUG=true
```

`VITE_CHECKOUT_KIT_DEBUG` is for local diagnosis only. Keep it `false` in production. No Shopify client secret or reusable bearer token belongs in the frontend environment.

## Run

1. Start PostgreSQL with `docker compose up -d postgres`.
2. Start the backend with `mvn spring-boot:run -f backend/pom.xml`.
3. Start the frontend with `bun run dev` from `frontend/`.
4. Sign in to Meant and search for a purchasable Shopify Global Catalog product.
5. Open the grouped product, choose one exact in-stock offer, and add it to cart.
6. Start checkout for that merchant cart.
7. Confirm the item has not been added to Inventory while checkout is bootstrapping or merely ready.
8. Wait for “Secure checkout is ready,” then click “Open secure checkout.” The explicit click is required so browsers do not treat the popup as unsolicited. If the popup fails before Checkout Kit emits `checkout:start`, confirm Inventory remains unchanged.
9. Once Meant reports “Checkout is active,” open Inventory and confirm the server-side cart item appears once with its selected options.
10. Close and reopen the same checkout. Confirm the new short-lived embedded session retains the same checkout-attempt identity and another `checkout:start` does not change inventory quantity.
11. Complete the development-shop checkout in the Shopify window.
12. Confirm that Meant reports completion only after the backend refreshes Checkout MCP and verifies provider state, without adding the checkout to Inventory again.

## Expected behavior

- The Meant top-level page does not navigate away.
- Checkout Kit opens a merchant popup and speaks ECP `2026-04-08`.
- The browser receives an opaque Meant session and checkout URL, never Shopify client credentials or the reusable global API token.
- Bootstrap, Checkout Kit readiness, and an `open()` attempt do not add inventory; only the mapped `checkout:start` event acknowledges the checkout as opened.
- Repeated starts, refreshes, remounts, and provider completion keep one inventory attribution for the logical checkout attempt.
- Purchased inventory retains server-side commerce identity and typed selected options; legacy, manual, and photo inventory remain readable without them.
- Closing Checkout Kit preserves the cart and allows a fresh session to be prepared.
- Unsupported browsers, disabled rollout, protocol mismatch, SDK failure, and startup timeout expose the merchant-provided external handoff when available.
- Reloading Meant preserves the merchant cart and checkout-attempt identity; reopening checkout refreshes the existing remote checkout and creates a new short-lived embedded session.

## Failure diagnosis

- `EXTERNAL_HANDOFF`: inspect the bootstrap reason and confirm the Shopify profile advertises checkout, the trusted merchant MCP response returned a `continue_url`, and the embedded readiness policy is enabled. Shopify ECP starts from `requires_escalation` plus `continue_url`; it does not require a separate embedded service advertisement in the Checkout MCP response.
- `UNSUPPORTED_PROTOCOL`: Checkout Kit and the configured ECP version must both use `2026-04-08`; base PCOS-016 requests no native payment/address delegations and supports no merchant-defined `ec_auth` exchange.
- Authentication or readiness failure: confirm TOKEN tier, Shopify agent auth, and both embedded readiness flags.
- Popup does not start: allow popups for `localhost:3000`, retry from the explicit “Open secure checkout” button, or use the fallback link.
- Completion cannot be verified: use “Reconcile checkout”; Meant does not trust the frontend completion event without provider confirmation.

After testing, disable `COMMERCE_EMBEDDED_CHECKOUT_ROLLOUT_ENABLED` and `VITE_EMBEDDED_CHECKOUT_ENABLED` unless the environment is intentionally part of the rollout.
