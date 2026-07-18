package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import tools.jackson.databind.JsonNode;

public record MerchantMcpToolCallResult(
        String endpoint,
        String contentText,
        JsonNode structuredContent,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public MerchantMcpToolCallResult(String endpoint, String contentText) {
        this(endpoint, contentText, null, NegotiatedCapabilities.none());
    }
}
