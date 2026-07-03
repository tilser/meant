package com.meant.api.module.discount.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "discount.code-search")
public record DiscountCodeSearchProperties(
        @NotNull
        Duration candidateCacheTtl,

        @NotNull
        Duration invalidCacheTtl,

        @NotNull
        Duration failedCacheTtl,

        @Positive
        int maxCandidates,

        @NotBlank
        String openRouterModel,

        @Positive
        int webMaxResults
) {

    @AssertTrue(message = "candidate-cache-ttl must be positive")
    public boolean isCandidateCacheTtlPositive() {
        return positive(candidateCacheTtl);
    }

    @AssertTrue(message = "invalid-cache-ttl must be positive")
    public boolean isInvalidCacheTtlPositive() {
        return positive(invalidCacheTtl);
    }

    @AssertTrue(message = "failed-cache-ttl must be positive")
    public boolean isFailedCacheTtlPositive() {
        return positive(failedCacheTtl);
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
