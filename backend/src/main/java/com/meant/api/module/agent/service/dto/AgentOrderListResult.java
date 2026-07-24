package com.meant.api.module.agent.service.dto;

import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.service.dto.OrderListResult;
import com.meant.api.module.order.service.dto.OrderSummaryResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentOrderListResult(
        List<Order> orders,
        int page,
        int limit,
        boolean hasNext
) {

    public AgentOrderListResult {
        orders = orders == null ? List.of() : List.copyOf(orders);
    }

    public static AgentOrderListResult from(OrderListResult result) {
        return new AgentOrderListResult(
                result.orders().stream().map(Order::from).toList(),
                result.page(),
                result.limit(),
                result.hasNext()
        );
    }

    public record Order(
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
            Instant createdAt,
            Instant updatedAt
    ) {

        public static Order from(OrderSummaryResult result) {
            return new Order(
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
                    result.createdAt(),
                    result.updatedAt()
            );
        }
    }
}
