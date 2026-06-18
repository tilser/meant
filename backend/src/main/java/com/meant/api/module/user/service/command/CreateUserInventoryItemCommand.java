package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateUserInventoryItemCommand(
        @NotNull
        UUID userId,

        @NotNull
        UserInventorySource source,

        @Size(max = 500)
        String sourceProductKey,

        @Size(max = 500)
        String productHash,

        @NotBlank
        @Size(max = 500)
        String name,

        @Size(max = 500)
        String brand,

        @NotNull
        UserInventoryCategory category,

        @Size(max = 2000)
        String description,

        @Size(max = 2048)
        String imageUrl,

        @Size(max = 2048)
        String productUrl,

        @Size(max = 2048)
        String photoUrl,

        @NotNull
        @Positive
        Integer quantity,

        @Size(max = 100)
        String unit,

        @Size(max = 500)
        String location,

        @Size(max = 2000)
        String notes,

        List<@NotBlank @Size(max = 200) String> attributes,

        @NotNull
        Boolean consumable,

        @NotNull
        Boolean restockEnabled,

        @PositiveOrZero
        Integer restockThreshold,

        Instant purchasedAt
) {

    public CreateUserInventoryItemCommand {
        source = source == null ? UserInventorySource.MANUAL : source;
        category = category == null ? UserInventoryCategory.OTHER : category;
        quantity = quantity == null ? 1 : quantity;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        consumable = consumable == null ? Boolean.FALSE : consumable;
        restockEnabled = restockEnabled == null ? Boolean.FALSE : restockEnabled;
    }
}
