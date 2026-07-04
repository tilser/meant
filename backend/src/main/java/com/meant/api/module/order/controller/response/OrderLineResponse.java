package com.meant.api.module.order.controller.response;

import com.meant.api.module.order.service.dto.OrderLineResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record OrderLineResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String variantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sku,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String unitAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency
) {

    public static OrderLineResponse from(OrderLineResult result) {
        return new OrderLineResponse(
                result.id(),
                result.productKey(),
                result.productId(),
                result.productTitle(),
                result.merchantName(),
                result.productVariantId(),
                result.variantTitle(),
                result.sku(),
                result.imageUrl(),
                result.productUrl(),
                result.quantity(),
                result.unitAmount(),
                result.totalAmount(),
                result.currency()
        );
    }
}
