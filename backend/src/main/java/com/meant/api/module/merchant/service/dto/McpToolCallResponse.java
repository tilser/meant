package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolCallResponse(
        String jsonrpc,
        int id,
        McpToolResult result,
        McpError error
) {
}
