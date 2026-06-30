package com.meant.api.module.order.service.dto;

import java.util.UUID;

public record OrderLineResult(
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
}
