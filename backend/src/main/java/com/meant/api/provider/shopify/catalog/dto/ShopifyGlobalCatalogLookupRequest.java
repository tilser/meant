package com.meant.api.provider.shopify.catalog.dto;

import java.util.List;

public record ShopifyGlobalCatalogLookupRequest(
        List<String> ids,
        ShopifyCatalogContext context,
        ShopifyCatalogFilters filters
) {
}
