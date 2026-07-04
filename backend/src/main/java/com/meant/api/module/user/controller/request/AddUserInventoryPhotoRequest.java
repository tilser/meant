package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserInventoryCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddUserInventoryPhotoRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 2_000_000)
        String photoUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UserInventoryCategory category,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2000)
        String description,
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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank @Size(max = 200) String> attributes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean consumable,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean restockEnabled,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @PositiveOrZero
        Integer restockThreshold
) {
}
