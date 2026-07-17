package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;

public record UserProductSearchCatalogInput(
        String searchQuery,
        String cacheKey,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters,
        CatalogDiscoveryFilters discoveryFilters
) {

    public UserProductSearchCatalogInput(
            String searchQuery,
            String cacheKey,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters
    ) {
        this(searchQuery, cacheKey, context, signals, filters, null);
    }
}
