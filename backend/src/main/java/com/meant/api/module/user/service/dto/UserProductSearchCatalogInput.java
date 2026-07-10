package com.meant.api.module.user.service.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;

public record UserProductSearchCatalogInput(
        String searchQuery,
        String cacheKey,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters
) {
}
