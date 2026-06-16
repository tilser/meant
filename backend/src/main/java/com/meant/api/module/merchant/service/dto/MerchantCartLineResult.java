package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.entity.MerchantCartLine;
import java.time.Instant;
import java.util.UUID;

public record MerchantCartLineResult(
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

    public static MerchantCartLineResult from(MerchantCartLine line) {
        return new MerchantCartLineResult(
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
