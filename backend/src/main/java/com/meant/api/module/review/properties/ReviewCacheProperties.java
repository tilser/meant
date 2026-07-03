package com.meant.api.module.review.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "review.cache")
public record ReviewCacheProperties(

        @NotNull
        Duration ttl,

        @NotNull
        @Positive
        Long maximumSize
) {

    @AssertTrue(message = "ttl must be positive")
    public boolean isTtlPositive() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
