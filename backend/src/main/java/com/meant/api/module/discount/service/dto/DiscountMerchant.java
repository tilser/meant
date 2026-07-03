package com.meant.api.module.discount.service.dto;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.UUID;

public record DiscountMerchant(
        UUID id,
        String domain,
        String name,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        boolean nativeCheckoutEnabled
) {

    public MerchantCartProvider cartProvider() {
        return new MerchantCartProvider(
                id,
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint,
                nativeCheckoutEnabled
        );
    }
}
