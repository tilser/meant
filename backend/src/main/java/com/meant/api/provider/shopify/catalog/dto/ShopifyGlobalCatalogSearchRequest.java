package com.meant.api.provider.shopify.catalog.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;

public record ShopifyGlobalCatalogSearchRequest(
        String query,
        ShopifyCatalogItemReference itemReference,
        ShopifyCatalogContext context,
        CatalogSearchSignals signals,
        ShopifyCatalogFilters filters,
        Integer limit,
        String cursor
) {

    public ShopifyGlobalCatalogSearchRequest(
            String query,
            ShopifyCatalogItemReference itemReference,
            ShopifyCatalogContext context,
            ShopifyCatalogFilters filters,
            Integer limit,
            String cursor
    ) {
        this(query, itemReference, context, null, filters, limit, cursor);
    }

    public ShopifyGlobalCatalogSearchRequest(
            String query,
            ShopifyCatalogContext context,
            ShopifyCatalogFilters filters,
            Integer limit,
            String cursor
    ) {
        this(query, null, context, null, filters, limit, cursor);
    }

    public ShopifyGlobalCatalogSearchRequest(String query, ShopifyCatalogContext context, ShopifyCatalogFilters filters) {
        this(query, null, context, null, filters, null, null);
    }
}
