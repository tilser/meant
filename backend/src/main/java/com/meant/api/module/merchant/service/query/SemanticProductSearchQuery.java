package com.meant.api.module.merchant.service.query;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record SemanticProductSearchQuery(
        @NotBlank
        String query,

        UUID merchantId,

        @Positive
        @Max(1000)
        Integer merchantCandidateLimit,

        @Positive
        @Max(20)
        Integer merchantLimit,

        @Positive
        @Max(50)
        Integer productsPerMerchant,

        @Positive
        @Max(100)
        Integer productLimit,

        @Valid
        CatalogSearchContext context,

        @Valid
        CatalogSearchSignals signals,

        @Valid
        CatalogSearchFilters filters
) {

    public SemanticProductSearchQuery(
            String query,
            UUID merchantId,
            Integer merchantCandidateLimit,
            Integer merchantLimit,
            Integer productsPerMerchant,
            Integer productLimit
    ) {
        this(query, merchantId, merchantCandidateLimit, merchantLimit, productsPerMerchant, productLimit, null, null, null);
    }
}
