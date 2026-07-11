package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record ListMerchantIntegrationsByIdsQuery(@NotEmpty Set<UUID> integrationIds) {
    public ListMerchantIntegrationsByIdsQuery {
        integrationIds = integrationIds == null ? Set.of() : Set.copyOf(integrationIds);
    }
}
