package com.meant.api.module.catalog.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.catalog.federation")
public record FederatedCatalogDiscoveryProperties(@NotNull Duration overallDeadline) {

    @AssertTrue(message = "overallDeadline must be positive")
    public boolean hasPositiveDeadline() {
        return overallDeadline != null && !overallDeadline.isZero() && !overallDeadline.isNegative();
    }
}
