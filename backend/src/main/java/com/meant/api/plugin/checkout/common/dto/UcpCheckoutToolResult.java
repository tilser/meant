package com.meant.api.plugin.checkout.common.dto;

public record UcpCheckoutToolResult(
        String endpoint,
        String rawResponse,
        UcpCheckoutResponse response
) {
}
