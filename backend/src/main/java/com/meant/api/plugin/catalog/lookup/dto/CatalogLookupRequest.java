package com.meant.api.plugin.catalog.lookup.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;

public record CatalogLookupRequest(
        String productId,
        CatalogSearchContext context
) {
}
