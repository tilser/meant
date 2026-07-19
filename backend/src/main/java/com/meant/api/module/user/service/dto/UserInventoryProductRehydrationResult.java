package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.user.constant.UserInventoryCategory;
import java.time.LocalDate;
import java.util.UUID;

/** Current catalog facts when available, plus a safe inventory-owned fallback anchor. */
public record UserInventoryProductRehydrationResult(
        UUID inventoryItemId,
        UserInventoryCommerceReference commerceReference,
        CatalogProductRehydrationResult rehydration,
        String fallbackName,
        UserInventoryCategory fallbackCategory,
        String photoPath,
        String size,
        String color,
        String material,
        LocalDate purchasedOn
) {
    public boolean currentFactsAvailable() {
        return rehydration != null && rehydration.status() == CatalogRehydrationStatus.FRESH;
    }
}
