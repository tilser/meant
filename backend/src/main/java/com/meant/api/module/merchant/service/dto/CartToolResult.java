package com.meant.api.module.merchant.service.dto;

public record CartToolResult(
        String endpoint,
        String rawResponse,
        CartToolResponse response
) {
}
