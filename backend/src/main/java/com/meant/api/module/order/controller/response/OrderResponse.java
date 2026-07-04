package com.meant.api.module.order.controller.response;

import com.meant.api.module.order.service.dto.OrderResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteOrderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String orderNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String statusNote,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String date,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String subtotalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String orderStatusUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderLineResponse> lines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
