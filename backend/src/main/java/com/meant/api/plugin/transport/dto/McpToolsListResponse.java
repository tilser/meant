package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolsListResponse(
        String jsonrpc,
        int id,
        JsonNode result,
        McpError error
) {
}
