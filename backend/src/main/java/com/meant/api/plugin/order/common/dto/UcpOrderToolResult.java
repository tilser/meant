package com.meant.api.plugin.order.common.dto;

public record UcpOrderToolResult(
        String endpoint,
        String rawResponse,
        UcpOrderResponse response
) {
}
