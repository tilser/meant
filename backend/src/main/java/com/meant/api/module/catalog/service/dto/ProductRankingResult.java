package com.meant.api.module.catalog.service.dto;

import java.util.List;
import java.util.Map;

/** Canonical products reordered independently from their exact, independently ranked offers. */
public record ProductRankingResult(
        List<CanonicalProduct> products,
        Map<String, ProductRankingExplanation> productExplanations,
        Map<String, OfferRankingExplanation> offerExplanations
) {

    public ProductRankingResult {
        products = products == null ? List.of() : List.copyOf(products);
        productExplanations = productExplanations == null ? Map.of() : Map.copyOf(productExplanations);
        offerExplanations = offerExplanations == null ? Map.of() : Map.copyOf(offerExplanations);
    }
}
