package com.meant.api.plugin.spi;

public record UcpToolResponse(
        String textContent,
        Object structuredContent,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public UcpToolResponse {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
