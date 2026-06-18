package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import java.util.UUID;

public record UserInventoryRecommendationSignal(
        String productKey,
        UserInventoryRecommendationRelationship relationship,
        UUID inventoryItemId,
        String inventoryItemName,
        String reason
) {

    public static UserInventoryRecommendationSignal none(String productKey) {
        return new UserInventoryRecommendationSignal(
                productKey,
                UserInventoryRecommendationRelationship.NONE,
                null,
                null,
                ""
        );
    }
}
