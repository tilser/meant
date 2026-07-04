package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserInventoryItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserInventorySource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceProductKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserInventoryCategory category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String photoUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String unit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String location,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String notes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> attributes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean consumable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean restockEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer restockThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant purchasedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
