package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.spi.NegotiatedCapabilities;

public record MerchantMcpToolCallResult(
        String endpoint,
        String contentText,
        Object structuredContent,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public MerchantMcpToolCallResult(String endpoint, String contentText) {
        this(endpoint, contentText, null, NegotiatedCapabilities.none());
    }
}
