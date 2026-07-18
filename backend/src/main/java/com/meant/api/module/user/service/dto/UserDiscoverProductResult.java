package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import java.util.Map;

/** One currently rehydrated product in an historical Discover result set. */
public record UserDiscoverProductResult(
        CanonicalProduct product,
        UserCanonicalProductPersonalizationResult personalization,
        Map<String, UserOfferCommercialState> commercialStates
) {
    public UserDiscoverProductResult {
        personalization = personalization == null
                ? UserCanonicalProductPersonalizationResult.searchRelevance()
                : personalization;
        commercialStates = commercialStates == null ? Map.of() : Map.copyOf(commercialStates);
    }
}
