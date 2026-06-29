package com.meant.api.plugin.transport;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record McpToolCallRequest(
        String jsonrpc,
        int id,
        String method,
        McpToolCallParams params,
        Map<String, Object> meta
) {

    public McpToolCallRequest(String jsonrpc, int id, String method, McpToolCallParams params) {
        this(jsonrpc, id, method, params, Map.of());
    }
}
