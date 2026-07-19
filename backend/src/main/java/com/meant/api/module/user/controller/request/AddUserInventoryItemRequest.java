package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserInventoryCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "An owned item and the path of its uploaded private inventory photo.")
public record AddUserInventoryItemRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 1024)
        String photoPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UserInventoryCategory category,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2000)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2048)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 100)
        String unit,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String location,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2000)
        String notes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank @Size(max = 200) String> attributes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean consumable,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean restockEnabled,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @PositiveOrZero
        Integer restockThreshold,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        LocalDate purchasedOn,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 200)
        String size,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 200)
        String color,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 200)
        String material
) {
}
