package com.meant.api.provider.shopify.capability;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Stable, non-secret access readiness metadata used without acquiring a runtime bearer token. */
@Validated
@ConfigurationProperties(prefix = "shopify.capability-readiness")
public record ShopifyCapabilityReadinessProperties(
        @NotNull ShopifyAuthorizationTier authorizationTier,
        @NotNull Set<String> grantedScopes,
        @NotNull Boolean embeddedCheckoutAdvertised,
        @NotNull Boolean embeddedCheckoutAuthorized,
        @NotNull Boolean directCheckoutCompletionAdvertised,
        @NotNull Boolean directCheckoutCompletionAuthorized,
        @NotNull Set<String> directCheckoutCompletionRequiredScopes,
        @NotNull Boolean orderReadsAuthorized,
        @NotNull Set<String> orderReadsRequiredScopes,
        @NotNull Boolean orderWebhooksAuthorized
) {

    public ShopifyCapabilityReadinessProperties {
        grantedScopes = clean(grantedScopes);
        directCheckoutCompletionRequiredScopes = clean(directCheckoutCompletionRequiredScopes);
        orderReadsRequiredScopes = clean(orderReadsRequiredScopes);
    }

    public boolean isEmbeddedCheckoutAdvertised() {
        return Boolean.TRUE.equals(embeddedCheckoutAdvertised);
    }

    public boolean isEmbeddedCheckoutAuthorized() {
        return Boolean.TRUE.equals(embeddedCheckoutAuthorized);
    }

    public boolean isDirectCheckoutCompletionAdvertised() {
        return Boolean.TRUE.equals(directCheckoutCompletionAdvertised);
    }

    public boolean isDirectCheckoutCompletionAuthorized() {
        return Boolean.TRUE.equals(directCheckoutCompletionAuthorized);
    }

    public boolean isOrderReadsAuthorized() {
        return Boolean.TRUE.equals(orderReadsAuthorized);
    }

    public boolean isOrderWebhooksAuthorized() {
        return Boolean.TRUE.equals(orderWebhooksAuthorized);
    }

    private static Set<String> clean(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
