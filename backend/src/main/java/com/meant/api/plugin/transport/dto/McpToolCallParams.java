package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record McpToolCallParams(
        String name,
        JsonNode arguments
) {
}
