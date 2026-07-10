package com.meant.api.plugin.catalog.shopify.dto;

import java.util.List;

public record ShopifyGlobalCatalogGetProductRequest(
        String id,
        List<ShopifyCatalogSelectedOption> selected,
        List<String> preferences,
        ShopifyCatalogContext context,
        ShopifyCatalogFilters filters
) {
}
