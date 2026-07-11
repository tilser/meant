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
7. Wait for “Secure checkout is ready,” then click “Open secure checkout.” The explicit click is required so browsers do not treat the popup as unsolicited.
8. Complete the development-shop checkout in the Shopify window.
9. Confirm that Meant reports completion only after the backend refreshes Checkout MCP and verifies provider state.

## Expected behavior

- The Meant top-level page does not navigate away.
- Checkout Kit opens a merchant popup and speaks ECP `2026-04-08`.
- The browser receives an opaque Meant session and checkout URL, never Shopify client credentials or the reusable global API token.
- Closing Checkout Kit preserves the cart and allows a fresh session to be prepared.
- Unsupported browsers, disabled rollout, protocol mismatch, SDK failure, and startup timeout expose the validated external merchant handoff when available.
- Reloading Meant preserves the merchant cart; reopening checkout refreshes the existing remote checkout and creates a new short-lived embedded session.

## Failure diagnosis

- `EXTERNAL_HANDOFF`: inspect the bootstrap reason and confirm the merchant checkout response advertises the `dev.ucp.shopping` embedded service binding.
- `UNSUPPORTED_PROTOCOL`: Checkout Kit and the merchant binding must both use ECP `2026-04-08`; base PCOS-016 requests no delegations and supports no merchant-defined `ec_auth` exchange.
- Authentication or readiness failure: confirm TOKEN tier, Shopify agent auth, and both embedded readiness flags.
- Popup does not start: allow popups for `localhost:3000`, retry from the explicit “Open secure checkout” button, or use the fallback link.
- Completion cannot be verified: use “Reconcile checkout”; Meant does not trust the frontend completion event without provider confirmation.

After testing, disable `COMMERCE_EMBEDDED_CHECKOUT_ROLLOUT_ENABLED` and `VITE_EMBEDDED_CHECKOUT_ENABLED` unless the environment is intentionally part of the rollout.
