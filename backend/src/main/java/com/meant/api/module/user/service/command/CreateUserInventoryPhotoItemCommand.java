package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserInventoryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateUserInventoryPhotoItemCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 2_000_000)
        String photoUrl,

        @Size(max = 500)
        String name,

        @Size(max = 500)
        String brand,

        UserInventoryCategory category,

        @Size(max = 2000)
        String description,

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

        Boolean consumable,

        @NotNull
        Boolean restockEnabled,

        @PositiveOrZero
        Integer restockThreshold
) {

    public CreateUserInventoryPhotoItemCommand {
        quantity = quantity == null ? 1 : quantity;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        restockEnabled = restockEnabled == null ? Boolean.FALSE : restockEnabled;
    }
}
