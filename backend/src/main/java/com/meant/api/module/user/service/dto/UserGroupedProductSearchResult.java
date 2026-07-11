package com.meant.api.module.user.service.dto;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecision;
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
        List<CanonicalProduct> products,
        List<ProductGroupingDecision> groupingDecisions
) {

    public UserGroupedProductSearchResult {
        products = products == null ? List.of() : List.copyOf(products);
        groupingDecisions = groupingDecisions == null ? List.of() : List.copyOf(groupingDecisions);
    }
}
