package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserInventoryCategory;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUserInventoryItemRequest(
        @Size(max = 500)
        String name,

        @Size(max = 500)
        String brand,

        UserInventoryCategory category,

        @Size(max = 2000)
        String description,

        @Size(max = 2048)
        String imageUrl,

        @Size(max = 2048)
        String productUrl,

        @Size(max = 2048)
        String photoUrl,

        @Positive
        Integer quantity,

        @Size(max = 100)
        String unit,

        @Size(max = 500)
        String location,

        @Size(max = 2000)
        String notes,

        List<@Size(max = 200) String> attributes,

        Boolean consumable,

        Boolean restockEnabled,

        @PositiveOrZero
        Integer restockThreshold
) {
}
