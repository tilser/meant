# Shopify Global Catalog data-use policy

Shopify Global Catalog search facts and media are session-only by default. Search responses may be
rendered directly for the active listing session, including HTTPS merchant image URLs, but Meant
does not download those images or copy the URLs and payload into its durable search cache.

Durable saves retain only the provider, discovery source, merchant/product/variant references,
selected options, and local routing identifiers needed for a later lookup/get-product call. Price,
availability, fulfillment, remote media, and offer payloads are rehydrated and are not authoritative
when read from a persisted display or transaction snapshot.

The executable rules live in `ShopifyCatalogDataUsePolicy`. A later legal/product approval must set
`SHOPIFY_GLOBAL_CATALOG_SEARCH_PERSISTENCE_APPROVED=true` and choose a reviewed bounded value for
`SHOPIFY_GLOBAL_CATALOG_APPROVED_SEARCH_CACHE_TTL`. Changing only the legacy user-search TTL cannot
admit Shopify data. Deploy the approval and TTL together; the policy fingerprint invalidates rows
whose source policy no longer matches the current configuration.

Policy and rehydration metrics use controlled policy/status/failure tags only. Queries, URLs, user or
buyer identifiers, provider payloads, and credentials are never metric tags or policy log fields.
