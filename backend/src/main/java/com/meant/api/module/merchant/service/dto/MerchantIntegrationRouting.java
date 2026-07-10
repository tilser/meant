package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import java.util.Set;
import java.util.UUID;

public record MerchantIntegrationRouting(
        UUID integrationId,
        MerchantIntegrationProvider provider,
        Set<MerchantIntegrationRole> roles,
        MerchantIntegrationStatus status,
        String externalMerchantId,
        String verifiedDomain,
        String verifiedShopIdentity,
        String endpoint
) {

    public MerchantIntegrationRouting {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
