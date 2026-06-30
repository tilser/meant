package com.meant.api.module.order.controller.response;

import com.meant.api.module.order.service.dto.OrderLineResult;
import java.util.UUID;

public record OrderLineResponse(
        UUID id,
        String productKey,
        String productId,
        String productTitle,
        String merchantName,
        String productVariantId,
        String variantTitle,
        String sku,
        String imageUrl,
        String productUrl,
        Integer quantity,
        String unitAmount,
        String totalAmount,
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
