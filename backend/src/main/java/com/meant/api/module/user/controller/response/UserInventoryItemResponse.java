package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserInventoryItemResponse(
        UUID id,
        UserInventorySource source,
        String sourceProductKey,
        String productHash,
        String name,
        String brand,
        UserInventoryCategory category,
        String description,
        String imageUrl,
        String productUrl,
        String photoUrl,
        int quantity,
        String unit,
        String location,
        String notes,
        List<String> attributes,
        boolean consumable,
        boolean restockEnabled,
        Integer restockThreshold,
        Instant purchasedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserInventoryItemResponse from(UserInventoryItemResult result) {
        return new UserInventoryItemResponse(
                result.id(),
                result.source(),
                result.sourceProductKey(),
                result.productHash(),
                result.name(),
                result.brand(),
                result.category(),
                result.description(),
                result.imageUrl(),
                result.productUrl(),
                result.photoUrl(),
                result.quantity(),
                result.unit(),
                result.location(),
                result.notes(),
                result.attributes(),
                result.consumable(),
                result.restockEnabled(),
                result.restockThreshold(),
                result.purchasedAt(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
