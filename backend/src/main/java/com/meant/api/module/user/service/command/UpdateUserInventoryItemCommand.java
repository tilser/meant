package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserInventoryCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UpdateUserInventoryItemCommand(
        @NotNull
        UUID userId,

        @NotNull
        UUID itemId,

        @Size(max = 1024)
        String photoPath,

        @Size(max = 500)
        String name,

        @Size(max = 500)
        String brand,

        UserInventoryCategory category,

        @Size(max = 2000)
        String description,

        @Size(max = 2048)
        String productUrl,

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
        Integer restockThreshold,

        @Pattern(regexp = "^$|\\d{4}-\\d{2}-\\d{2}$")
        String purchasedOn,

        @Size(max = 200)
        String size,

        @Size(max = 200)
        String color,

        @Size(max = 200)
        String material
) {
}
