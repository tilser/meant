package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import java.util.List;
import java.util.Map;

/** Current transient product facts reconstructed from a durable identifiers-only anchor. */
public record UserCanonicalProductRehydrationResult(
        CanonicalProduct product,
        boolean currentFactsAvailable,
        Map<String, UserOfferCommercialState> commercialStates,
        List<UserCatalogSourceState> sourceStates
) {
    public UserCanonicalProductRehydrationResult {
        commercialStates = commercialStates == null ? Map.of() : Map.copyOf(commercialStates);
        sourceStates = sourceStates == null ? List.of() : List.copyOf(sourceStates);
    }
}
