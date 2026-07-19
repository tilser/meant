package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UserInventoryItemResult(
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
        String photoPath,
        int quantity,
        String unit,
        String location,
        String notes,
        String size,
        String color,
        String material,
        List<String> attributes,
        boolean consumable,
        boolean restockEnabled,
        Integer restockThreshold,
        Instant purchasedAt,
        LocalDate purchasedOn,
        UserInventoryCommerceReference commerceReference,
        UUID sourceCheckoutAttemptId,
        Instant createdAt,
        Instant updatedAt
) {
}
