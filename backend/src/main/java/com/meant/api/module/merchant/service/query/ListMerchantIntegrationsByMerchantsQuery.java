package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record ListMerchantIntegrationsByMerchantsQuery(
        @NotEmpty Set<@NotNull UUID> merchantIds
) {

    public ListMerchantIntegrationsByMerchantsQuery {
        merchantIds = merchantIds == null ? Set.of() : Set.copyOf(merchantIds);
    }
}
