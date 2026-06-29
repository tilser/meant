package com.meant.api.plugin.spi;

public record UcpToolResponse<TStructuredContent>(
        String textContent,
        TStructuredContent structuredContent,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public UcpToolResponse {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
