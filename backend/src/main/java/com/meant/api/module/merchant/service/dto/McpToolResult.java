package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolResult(
        List<McpContent> content,
        boolean isError
) {
}
