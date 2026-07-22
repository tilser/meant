package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;

public record ResolvedMerchantIdentityClaim(
        MerchantIdentityNamespace namespace,
        String normalizedValue,
        MerchantIdentityRole role
) {
}
