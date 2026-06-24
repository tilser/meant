package com.meant.api.module.user.service.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UserProductSearchCuratorResult(
        List<UserProductSearchProductResult> products,
        List<UserProductSearchProductResult> pageProducts,
        Map<String, UserProductRecommendationExplanationResult> explanations
) {

    public UserProductSearchCuratorResult {
        products = products == null ? List.of() : List.copyOf(products);
        pageProducts = pageProducts == null ? List.of() : List.copyOf(pageProducts);
        explanations = explanations == null ? Map.of() : new LinkedHashMap<>(explanations);
    }
}
