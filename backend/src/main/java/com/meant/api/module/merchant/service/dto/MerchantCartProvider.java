package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantCartProvider(
        UUID merchantId,
        String domain,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        boolean nativeCheckoutEnabled
) {

    public MerchantCartProvider(
            UUID merchantId,
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint
    ) {
        this(merchantId, domain, advertisedMcpEndpoint, profileMcpEndpoint, false);
    }
}
