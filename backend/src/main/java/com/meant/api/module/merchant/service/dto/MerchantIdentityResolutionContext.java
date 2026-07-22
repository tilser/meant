package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;

public record MerchantIdentityResolutionContext(
        String sourceDomain,
        MerchantIntegrationProvider observedProvider,
        String observedProviderMerchantId,
        UcpProfile profile
) {

    public MerchantIdentityResolutionContext(
            String sourceDomain,
            String observedProviderMerchantId,
            UcpProfile profile
    ) {
        this(sourceDomain, null, observedProviderMerchantId, profile);
    }
}
