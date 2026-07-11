package com.meant.api.provider.shopify.cart;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shopify.cart")
public record ShopifyCartProperties(
        @NotNull Duration profileFreshness,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration requestDeadline,
        @NotNull Duration maxRetryDelay
) {
    @AssertTrue(message = "Shopify cart routing profile freshness must be positive")
    public boolean hasPositiveDurations() {
        return positive(profileFreshness) && positive(connectTimeout)
                && positive(readTimeout) && positive(requestDeadline) && positive(maxRetryDelay);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
