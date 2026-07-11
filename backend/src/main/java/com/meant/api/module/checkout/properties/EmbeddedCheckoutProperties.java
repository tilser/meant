package com.meant.api.module.checkout.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.checkout.embedded")
public record EmbeddedCheckoutProperties(
        @NotNull Duration sessionTtl,
        @NotBlank String supportedProtocolVersion
) {
    @AssertTrue(message = "Embedded checkout session TTL must be positive")
    public boolean valid() {
        return sessionTtl != null && !sessionTtl.isZero() && !sessionTtl.isNegative();
    }
}
