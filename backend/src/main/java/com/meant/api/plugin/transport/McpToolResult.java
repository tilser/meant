package com.meant.api.plugin.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolResult(
        List<McpContent> content,
        boolean isError,
        Object structuredContent
) {
}
