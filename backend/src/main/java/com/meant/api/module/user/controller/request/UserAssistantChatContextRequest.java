package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserAssistantChatContextRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 40)
        String view,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 120)
        String contextLabel,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String currentSearchQuery,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 160)
        String selectedMerchantName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer savedProductCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer cartItemCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 12)
        List<@Valid Product> visibleProducts,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 12)
        List<@Valid CartItem> cartItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 8)
        List<@Valid Order> orders
) {

    public record Product(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 160)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 220)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 160)
            String brand,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 120)
            String category,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer match,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Double priceFrom,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 500)
            String note
    ) {
    }

    public record CartItem(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 220)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 160)
            String merchant,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer quantity,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Double price
    ) {
    }

    public record Order(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 80)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 40)
            String date,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 80)
            String status,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Size(max = 240)
            String statusNote,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer itemCount
    ) {
    }
}
