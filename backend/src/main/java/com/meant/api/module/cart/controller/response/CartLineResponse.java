package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartLineResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CartLineResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartLineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartLineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String variantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String subtotalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static CartLineResponse from(CartLineResult result) {
        return new CartLineResponse(
                result.cartLineId(),
                result.remoteCartLineId(),
                result.productId(),
                result.productTitle(),
                result.productVariantId(),
                result.variantTitle(),
                result.quantity(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
