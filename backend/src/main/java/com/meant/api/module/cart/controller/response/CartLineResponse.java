package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartLineResult;
import java.time.Instant;
import java.util.UUID;

public record CartLineResponse(
        UUID cartLineId,
        String remoteCartLineId,
        String productId,
        String productTitle,
        String productVariantId,
        String variantTitle,
        Integer quantity,
        String totalAmount,
        String subtotalAmount,
        String currency,
        Instant createdAt,
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
