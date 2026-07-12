package com.meant.api.module.merchant.service.query;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Set;

public record EvaluateObservedProviderPolicyQuery(
        @NotNull MerchantIntegrationProvider provider,
        @NotNull MerchantIntegrationAuthStrategy authStrategy,
        @NotNull Set<MerchantIntegrationRole> roles,
        @NotNull Set<String> advertisedCapabilities
) {
    public EvaluateObservedProviderPolicyQuery {
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        advertisedCapabilities = Set.copyOf(Objects.requireNonNull(
                advertisedCapabilities, "advertisedCapabilities must not be null"));
    }
}
