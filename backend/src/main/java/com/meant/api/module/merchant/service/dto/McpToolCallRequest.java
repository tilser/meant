package com.meant.api.module.merchant.service.dto;

public record McpToolCallRequest(
        String jsonrpc,
        int id,
        String method,
        McpToolCallParams params
) {
}
