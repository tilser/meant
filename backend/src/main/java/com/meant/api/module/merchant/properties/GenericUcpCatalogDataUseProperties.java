package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.catalog.data-use.generic-ucp")
public record GenericUcpCatalogDataUseProperties(
        @NotNull Duration searchCacheTtl,
        @NotNull Duration rehydratedFactsTtl
) {
    public GenericUcpCatalogDataUseProperties {
        requirePositive(searchCacheTtl, "search cache TTL");
        requirePositive(rehydratedFactsTtl, "rehydrated facts TTL");
    }

    private static void requirePositive(Duration duration, String label) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("Generic UCP " + label + " must be positive");
        }
    }
}
