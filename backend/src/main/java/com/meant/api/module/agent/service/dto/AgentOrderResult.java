package com.meant.api.module.agent.service.dto;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.service.dto.OrderLineResult;
import com.meant.api.module.order.service.dto.OrderResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentOrderResult(
        UUID id,
        UUID merchantId,
        String merchantOrigin,
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
        List<OrderLineResult> lines,
        Instant createdAt,
        Instant updatedAt
) {

    public static AgentOrderResult from(OrderResult result) {
        return new AgentOrderResult(
                result.id(),
                result.merchantId(),
                result.merchantDomain(),
                result.merchantName(),
                result.remoteOrderId(),
                result.displayId(),
                result.orderNumber(),
                result.state(),
                result.status(),
                result.statusNote(),
                result.date(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.totalQuantity(),
                result.orderStatusUrl(),
                result.lines(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
