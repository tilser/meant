package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import java.util.List;
import java.util.UUID;

public record UserProductRecommendationExplanationResult(
        String productKey,
        String productHash,
        String whyMeantForYou,
        List<String> matchedFilterIds,
        List<String> missedFilterIds,
        UserInventoryRecommendationRelationship inventoryRelationship,
        UUID inventoryItemId,
        String inventoryItemName
) {

    private static final String FALLBACK_EXPLANATION = "Matched your search from merchant catalog data.";

    public UserProductRecommendationExplanationResult(
            String productKey,
            String productHash,
            String whyMeantForYou,
            List<String> matchedFilterIds,
            List<String> missedFilterIds
    ) {
        this(
                productKey,
                productHash,
                whyMeantForYou,
                matchedFilterIds,
                missedFilterIds,
                UserInventoryRecommendationRelationship.NONE,
                null,
                null
        );
    }

    public static UserProductRecommendationExplanationResult fallback(
            String productKey,
            String productHash
    ) {
        return fallback(productKey, productHash, null);
    }

    public static UserProductRecommendationExplanationResult fallback(
            String productKey,
            String productHash,
            UserInventoryRecommendationSignal inventorySignal
    ) {
        UserInventoryRecommendationRelationship relationship = inventorySignal == null
                || inventorySignal.relationship() == null
                ? UserInventoryRecommendationRelationship.NONE
                : inventorySignal.relationship();
        return new UserProductRecommendationExplanationResult(
                productKey,
                productHash,
                FALLBACK_EXPLANATION,
                List.of(),
                List.of(),
                relationship,
                inventorySignal == null ? null : inventorySignal.inventoryItemId(),
                inventorySignal == null ? null : inventorySignal.inventoryItemName()
        );
    }
}
