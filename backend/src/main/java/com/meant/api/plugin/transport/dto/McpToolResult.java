package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolResult(
        List<McpContent> content,
        Boolean isError,
        JsonNode structuredContent
) {

    public McpToolResult {
        isError = Boolean.TRUE.equals(isError);
    }
}
