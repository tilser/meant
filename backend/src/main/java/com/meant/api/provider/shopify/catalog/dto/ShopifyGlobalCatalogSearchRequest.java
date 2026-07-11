package com.meant.api.provider.shopify.catalog.dto;

public record ShopifyGlobalCatalogSearchRequest(
        String query,
        ShopifyCatalogContext context,
        ShopifyCatalogFilters filters,
        Integer limit,
        String cursor
) {

    public ShopifyGlobalCatalogSearchRequest(String query, ShopifyCatalogContext context, ShopifyCatalogFilters filters) {
        this(query, context, filters, null, null);
    }
}
