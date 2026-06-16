package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.entity.CartLine;
import java.time.Instant;
import java.util.UUID;

public record CartLineResult(
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

    public static CartLineResult from(CartLine line) {
        return new CartLineResult(
                line.getId(),
                line.getRemoteCartLineId(),
                line.getProductId(),
                line.getProductTitle(),
                line.getProductVariantId(),
                line.getVariantTitle(),
                line.getQuantity(),
                line.getTotalAmount(),
                line.getSubtotalAmount(),
                line.getCurrency(),
                line.getCreatedAt(),
                line.getUpdatedAt()
        );
    }
}
