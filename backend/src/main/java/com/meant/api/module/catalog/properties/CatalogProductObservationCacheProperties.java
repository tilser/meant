package com.meant.api.module.catalog.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.catalog.observation-cache")
public record CatalogProductObservationCacheProperties(
        @NotNull Duration maximumTtl,
        @Positive long rehydrationMaximumSize,
        @Positive long detailMaximumSize
) {

    @AssertTrue(message = "maximumTtl must be positive")
    public boolean hasPositiveMaximumTtl() {
        return maximumTtl != null && !maximumTtl.isZero() && !maximumTtl.isNegative();
    }
}
