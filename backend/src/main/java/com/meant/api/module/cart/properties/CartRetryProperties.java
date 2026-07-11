package com.meant.api.module.cart.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.cart.retry")
public record CartRetryProperties(@NotNull Duration maxReconciliationDelay) {
    @AssertTrue(message = "Cart reconciliation retry delay must be positive")
    public boolean valid() {
        return maxReconciliationDelay != null && !maxReconciliationDelay.isZero()
                && !maxReconciliationDelay.isNegative();
    }
}
