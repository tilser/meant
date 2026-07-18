package com.meant.api.plugin.spi;

import tools.jackson.databind.JsonNode;

public record UcpToolResponse(
        String textContent,
        JsonNode structuredContent,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public UcpToolResponse {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
