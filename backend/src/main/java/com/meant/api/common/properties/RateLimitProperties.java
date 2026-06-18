package com.meant.api.common.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @NotNull Boolean enabled,
        @NotNull @Valid ExpensiveEndpoints expensiveEndpoints
) {

    public record ExpensiveEndpoints(
            @NotEmpty List<@Valid Endpoint> endpoints,
            @NotEmpty List<@Valid Limit> limits,
            @NotNull @Valid BucketCache bucketCache
    ) {
    }

    public record Endpoint(
            @NotBlank String method,
            @NotBlank String path
    ) {
    }

    public record Limit(
            @NotBlank String name,
            @NotNull @Positive Integer capacity,
            @NotNull @Positive Integer refillTokens,
            @NotNull Duration refillPeriod
    ) {

        @AssertTrue(message = "refillPeriod must be positive")
        public boolean isRefillPeriodPositive() {
            return refillPeriod != null && !refillPeriod.isZero() && !refillPeriod.isNegative();
        }
    }

    public record BucketCache(
            @NotNull @Positive Long maximumSize,
            @NotNull Duration expireAfterAccess
    ) {

        @AssertTrue(message = "expireAfterAccess must be positive")
        public boolean isExpireAfterAccessPositive() {
            return expireAfterAccess != null && !expireAfterAccess.isZero() && !expireAfterAccess.isNegative();
        }
    }
}
