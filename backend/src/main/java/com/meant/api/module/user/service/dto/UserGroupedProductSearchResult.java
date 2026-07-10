package com.meant.api.module.user.service.dto;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import java.util.List;

public record UserGroupedProductSearchResult(
        String query,
        String normalizedQuery,
        String profileHash,
        boolean cached,
        int offset,
        int limit,
        Integer nextOffset,
        boolean hasMore,
        List<CanonicalProduct> products
) {

    public UserGroupedProductSearchResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
