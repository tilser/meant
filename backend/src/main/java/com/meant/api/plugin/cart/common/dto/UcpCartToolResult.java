package com.meant.api.plugin.cart.common.dto;

public record UcpCartToolResult(
        String endpoint,
        String rawResponse,
        UcpCartResponse response
) {
}
