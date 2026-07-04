package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.enrichment")
public record MerchantEnrichmentProperties(

        @Positive
        @NotNull
        Integer batchSize,

        @Positive
        @NotNull
        Long fixedDelay,

        @NotNull
        Duration retryDelay
) {

    @AssertTrue(message = "retryDelay must be positive")
    public boolean isRetryDelayPositive() {
        return retryDelay != null && !retryDelay.isZero() && !retryDelay.isNegative();
    }
}
