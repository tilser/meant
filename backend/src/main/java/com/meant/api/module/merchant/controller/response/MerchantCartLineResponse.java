package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantCartLineResult;
import java.time.Instant;
import java.util.UUID;

public record MerchantCartLineResponse(
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

    public static MerchantCartLineResponse from(MerchantCartLineResult result) {
        return new MerchantCartLineResponse(
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
