package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.ucp-profile-observation")
public record MerchantUcpProfileObservationProperties(
        @NotNull Duration ttl,
        @Positive long maximumSize
) {
    @AssertTrue(message = "Merchant UCP profile observation TTL must be positive")
    public boolean hasPositiveTtl() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
