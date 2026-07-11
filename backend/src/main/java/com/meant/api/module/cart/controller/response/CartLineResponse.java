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
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productVariantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String variantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer quantity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String subtotalAmount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String currency,
        @Schema(description = "Server-issued exact offer key bound to this line",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String offerKey,
        @Schema(description = "Immutable commerce provider scope", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String provider,
        @Schema(description = "Verified local integration route when one exists",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantIntegrationId,
        @Schema(description = "Provider-scoped external seller identity",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String externalMerchantId,
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
                result.offerKey(),
                result.provider(),
                result.merchantIntegrationId(),
                result.externalMerchantId(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
