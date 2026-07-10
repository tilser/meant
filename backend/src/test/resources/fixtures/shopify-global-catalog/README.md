# Shopify Global Catalog contract fixtures

These deterministic, credential-free fixtures model Shopify's published UCP `2026-04-08`
Global Catalog examples and `dev.shopify.catalog.global` `2026-04-08` extension.

Contract assumptions exercised by the adapter:

- `gid://shopify/p/{upid}` is trusted Shopify UPID grouping evidence.
- Every Global Catalog offer has `variant.seller.id`; a missing seller ID is malformed because
  commercial offer identity cannot safely fall back to a local Meant merchant.
- Prices are integer ISO-4217 minor units.
- The published extension currently guarantees only `requires.selling_plan` and
  `requires.components`. Optional typed `selling_plan` and `components` payloads are accepted only
  to preserve identity if Shopify supplies additive data; they are never synthesized.
- Unknown extension fields are ignored only at this provider DTO boundary.
- `get-product-success.json` exercises the singular product shape and required lookup capability.
- No fixture contains a live token, client secret, buyer data, or live Shopify response payload.
