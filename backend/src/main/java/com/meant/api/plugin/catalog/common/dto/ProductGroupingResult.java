package com.meant.api.plugin.catalog.common.dto;

import java.util.List;

public record ProductGroupingResult(
        List<CanonicalProduct> products,
        List<ProductGroupingDecision> decisions
) {

    public ProductGroupingResult {
        products = products == null ? List.of() : List.copyOf(products);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
