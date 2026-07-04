package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ShoppingFilterResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String polarity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer displayOrder
) {

    public static ShoppingFilterResponse from(ShoppingFilterResult filter) {
        return new ShoppingFilterResponse(
                filter.id(),
                filter.label(),
                filter.description(),
                filter.category(),
                filter.polarity(),
                filter.displayOrder()
        );
    }
}
