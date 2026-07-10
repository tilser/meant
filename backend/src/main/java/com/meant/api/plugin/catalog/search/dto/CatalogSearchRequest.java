package com.meant.api.plugin.catalog.search.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;

public record CatalogSearchRequest(
        String query,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters,
        int limit
) {
}
