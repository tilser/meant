package com.meant.api.plugin.catalog.lookup.dto;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;

public record CatalogLookupRequest(
        String productId,
        CatalogSearchContext context
) {
}
