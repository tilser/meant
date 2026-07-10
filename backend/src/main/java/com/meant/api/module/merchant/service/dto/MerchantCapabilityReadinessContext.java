package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import java.util.Set;

public record MerchantCapabilityReadinessContext(
        MerchantIntegrationProvider provider,
        MerchantIntegrationAuthStrategy authStrategy,
        Set<MerchantIntegrationRole> roles,
        Set<String> advertisedCapabilities
) {

    public MerchantCapabilityReadinessContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        advertisedCapabilities = advertisedCapabilities == null ? Set.of() : Set.copyOf(advertisedCapabilities);
    }

    public boolean hasRole(MerchantIntegrationRole role) {
        return roles.contains(role);
    }

    public boolean advertises(String capability) {
        return advertisedCapabilities.contains(capability);
    }
}
