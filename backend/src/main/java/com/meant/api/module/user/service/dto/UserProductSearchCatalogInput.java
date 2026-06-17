package com.meant.api.module.user.service.dto;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;

public record UserProductSearchCatalogInput(
        String searchQuery,
        String cacheKey,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters
) {
}
