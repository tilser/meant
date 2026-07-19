package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserInventoryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateUserInventoryItemCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 1024)
        String photoPath,

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
        String productUrl,

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

        LocalDate purchasedOn,

        @Size(max = 200)
        String size,

        @Size(max = 200)
        String color,

        @Size(max = 200)
        String material
) {

    public CreateUserInventoryItemCommand {
        quantity = quantity == null ? 1 : quantity;
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        consumable = consumable == null ? Boolean.FALSE : consumable;
        restockEnabled = restockEnabled == null ? Boolean.FALSE : restockEnabled;
    }
}
