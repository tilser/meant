package com.meant.api.module.cart.service.dto;

public record CartToolResult(
        String endpoint,
        String rawResponse,
        CartToolResponse response
) {
}
