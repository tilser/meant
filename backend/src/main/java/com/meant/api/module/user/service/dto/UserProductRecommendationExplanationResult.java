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
}
