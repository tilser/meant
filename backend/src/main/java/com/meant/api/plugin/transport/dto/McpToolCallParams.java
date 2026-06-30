package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record McpToolCallParams(
        String name,
        Object arguments
) {
}
