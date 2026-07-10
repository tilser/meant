package com.meant.api.plugin.transport.profile;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ucp.capability-readiness")
public record GenericUcpCapabilityReadinessProperties(
        @NotNull Boolean directCheckoutCompletionAuthorized,
        @NotNull Boolean orderReadsAuthorized,
        @NotNull Boolean orderWebhooksAuthorized
) {

    public boolean isDirectCheckoutCompletionAuthorized() {
        return Boolean.TRUE.equals(directCheckoutCompletionAuthorized);
    }

    public boolean isOrderReadsAuthorized() {
        return Boolean.TRUE.equals(orderReadsAuthorized);
    }

    public boolean isOrderWebhooksAuthorized() {
        return Boolean.TRUE.equals(orderWebhooksAuthorized);
    }
}
