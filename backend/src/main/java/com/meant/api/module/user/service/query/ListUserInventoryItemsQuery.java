package com.meant.api.module.user.service.query;

import com.meant.api.module.user.constant.UserInventoryCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ListUserInventoryItemsQuery(
        @NotNull
        UUID userId,

        UserInventoryCategory category,

        Boolean restockOnly,

        @PositiveOrZero
        int page,

        @Positive
        int limit
) {

    public boolean restockOnlyValue() {
        return Boolean.TRUE.equals(restockOnly);
    }
}
