package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogSearchCatalog(
        String query,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters,
        CatalogSearchPagination pagination
) {

    public CatalogSearchCatalog(String query, CatalogSearchPagination pagination) {
        this(query, null, null, null, pagination);
    }
}
