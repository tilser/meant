package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record CatalogSearchResult(
        String endpoint,
        List<CatalogSearchResponse.Product> products
) {
}
