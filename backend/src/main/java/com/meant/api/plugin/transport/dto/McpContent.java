package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpContent(
        String type,
        String text
) {
}
