package com.meant.api.plugin.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolCallResponse(
        String jsonrpc,
        int id,
        McpToolResult result,
        McpError error
) {
}
