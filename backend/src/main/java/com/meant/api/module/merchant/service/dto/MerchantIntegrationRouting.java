package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import java.util.Objects;
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
        Objects.requireNonNull(integrationId, "integrationId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(status, "status must not be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
