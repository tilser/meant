package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import java.util.List;
import java.util.Map;

public record UserProductDetailResult(
        CanonicalProduct product,
        String recommendedOfferKey,
        String selectedOfferKey,
        ProductRankingExplanation productRankingExplanation,
        Map<String, OfferRankingExplanation> offerRankingExplanations,
        Map<String, UserOfferCommercialState> commercialStates,
        List<UserCatalogSourceState> sourceStates
) {
    public UserProductDetailResult {
        offerRankingExplanations = offerRankingExplanations == null ? Map.of() : Map.copyOf(offerRankingExplanations);
        commercialStates = commercialStates == null ? Map.of() : Map.copyOf(commercialStates);
        sourceStates = sourceStates == null ? List.of() : List.copyOf(sourceStates);
    }
}
