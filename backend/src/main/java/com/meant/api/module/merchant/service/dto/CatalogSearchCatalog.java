package com.meant.api.module.merchant.service.dto;

public record CatalogSearchCatalog(
        String query,
        CatalogSearchPagination pagination
) {
}
