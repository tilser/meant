package com.meant.api.module.order.service.dto;

import java.util.List;

public record OrderListResult(
        List<OrderSummaryResult> orders,
        int page,
        int limit,
        boolean hasNext
) {
}
