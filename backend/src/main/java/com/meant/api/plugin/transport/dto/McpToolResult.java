package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolResult(
        List<McpContent> content,
        Boolean isError,
        Object structuredContent
) {

    public McpToolResult {
        isError = Boolean.TRUE.equals(isError);
    }
}
