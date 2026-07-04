package com.meant.api.module.order.service.dto;

import com.meant.api.module.order.constant.OrderState;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResult(
        UUID id,
        UUID merchantId,
        String merchantDomain,
        String merchantName,
        String remoteOrderId,
        String displayId,
        String orderNumber,
        OrderState state,
        String status,
        String statusNote,
        String date,
        String totalAmount,
        String subtotalAmount,
        String currency,
        Integer totalQuantity,
        String orderStatusUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
