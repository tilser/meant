package com.meant.api.module.order.controller.response;

import com.meant.api.module.order.service.dto.OrderListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Paged order summary list.")
public record OrderListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderSummaryResponse> orders,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext
) {

    public static OrderListResponse from(OrderListResult result) {
        return new OrderListResponse(
                result.orders().stream().map(OrderSummaryResponse::from).toList(),
                result.page(),
                result.limit(),
                result.hasNext()
        );
    }
}
