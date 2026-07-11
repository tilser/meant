package com.meant.api.provider.shopify.cart;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shopify.cart")
public record ShopifyCartProperties(
        @NotNull Duration profileFreshness
) {
    @AssertTrue(message = "Shopify cart routing profile freshness must be positive")
    public boolean hasPositiveDurations() {
        return positive(profileFreshness);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
