package com.meant.api.module.review.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "review.provider-discovery")
public record ReviewProviderDiscoveryProperties(

        @NotNull
        @Positive
        Integer batchSize,

        @NotNull
        Duration fixedDelay,

        @NotNull
        Duration storefrontTimeout,

        @NotNull
        @Positive
        Integer storefrontMaxBytes,

        @NotNull
        Duration retryDelay,

        @NotNull
        Duration notFoundRecheckDelay
) {

    @AssertTrue(message = "fixedDelay must be positive")
    public boolean isFixedDelayPositive() {
        return isPositive(fixedDelay);
    }

    @AssertTrue(message = "storefrontTimeout must be positive")
    public boolean isStorefrontTimeoutPositive() {
        return isPositive(storefrontTimeout);
    }

    @AssertTrue(message = "retryDelay must be positive")
    public boolean isRetryDelayPositive() {
        return isPositive(retryDelay);
    }

    @AssertTrue(message = "notFoundRecheckDelay must be positive")
    public boolean isNotFoundRecheckDelayPositive() {
        return isPositive(notFoundRecheckDelay);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
