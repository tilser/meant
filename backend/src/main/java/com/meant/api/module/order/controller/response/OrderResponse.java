package com.meant.api.module.order.controller.response;

import com.meant.api.module.order.service.dto.OrderResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID merchantId,
        String merchantDomain,
        String merchantName,
        String remoteOrderId,
        String displayId,
        String orderNumber,
        String state,
        String status,
        String statusNote,
        String date,
        String totalAmount,
        String subtotalAmount,
        String currency,
        Integer totalQuantity,
        String orderStatusUrl,
        List<OrderLineResponse> lines,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(
                result.id(),
                result.merchantId(),
                result.merchantDomain(),
                result.merchantName(),
                result.remoteOrderId(),
                result.displayId(),
                result.orderNumber(),
                result.state().name(),
                result.status(),
                result.statusNote(),
                result.date(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.totalQuantity(),
                result.orderStatusUrl(),
                result.lines().stream().map(OrderLineResponse::from).toList(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
