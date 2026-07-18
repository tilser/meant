package com.meant.api.provider.shopify.catalog.dto;

public record ShopifyGlobalCatalogSearchRequest(
        String query,
        ShopifyCatalogItemReference itemReference,
        ShopifyCatalogContext context,
        ShopifyCatalogFilters filters,
        Integer limit,
        String cursor
) {

    public ShopifyGlobalCatalogSearchRequest(
            String query,
            ShopifyCatalogContext context,
            ShopifyCatalogFilters filters,
            Integer limit,
            String cursor
    ) {
        this(query, null, context, filters, limit, cursor);
    }

    public ShopifyGlobalCatalogSearchRequest(String query, ShopifyCatalogContext context, ShopifyCatalogFilters filters) {
        this(query, null, context, filters, null, null);
    }
}
