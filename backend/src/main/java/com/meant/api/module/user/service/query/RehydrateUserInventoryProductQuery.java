package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;

public record RehydrateUserInventoryProductQuery(
        @NotNull UUID userId,
        @NotNull UUID inventoryItemId,
        @Size(max = 2) String countryCode
) {
    public RehydrateUserInventoryProductQuery {
        countryCode = countryCode == null || countryCode.isBlank()
                ? null : countryCode.trim().toUpperCase(Locale.ROOT);
    }

    public RehydrateUserInventoryProductQuery(UUID userId, UUID inventoryItemId) {
        this(userId, inventoryItemId, null);
    }
}
