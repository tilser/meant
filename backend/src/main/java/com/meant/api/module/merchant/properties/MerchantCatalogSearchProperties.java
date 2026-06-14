package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.catalog-search")
public record MerchantCatalogSearchProperties(

        @Positive
        @Max(1000)
        @NotNull
        Integer merchantCandidateLimit,

        @Positive
        @Max(20)
        @NotNull
        Integer merchantLimit,

        @Positive
        @Max(50)
        @NotNull
        Integer productsPerMerchant,

        @Positive
        @Max(100)
        @NotNull
        Integer productLimit
) {
}
