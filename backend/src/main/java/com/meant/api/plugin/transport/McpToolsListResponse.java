package com.meant.api.plugin.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolsListResponse(
        String jsonrpc,
        int id,
        Object result,
        McpError error
) {
}
