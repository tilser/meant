package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record MerchantIdentityResolution(
        String canonicalDomain,
        String merchantName,
        List<ResolvedMerchantIdentityClaim> claims
) {

    public MerchantIdentityResolution {
        claims = claims == null ? List.of() : List.copyOf(claims);
    }
}
