package com.meant.api.plugin.catalog.shopify.dto;

import java.util.List;

public record ShopifyGlobalCatalogLookupRequest(
        List<String> ids,
        ShopifyCatalogContext context,
        ShopifyCatalogFilters filters
) {
}
