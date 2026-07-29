package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Prevents the request-wide federation deadline from truncating Shopify's source-owned budget.
 */
@Component
@RequiredArgsConstructor
public class ShopifyCatalogFederationTimeoutBudgetValidator {

    private final FederatedCatalogDiscoveryProperties federationProperties;
    private final ShopifyGlobalCatalogProperties catalogProperties;

    @PostConstruct
    void validate() {
        if (!catalogProperties.discoveryEnabled()) {
            return;
        }
        Duration requiredOverallDeadline = catalogProperties.discoverySourceTimeout();
        requireNanosCapacity(
                federationProperties.overallDeadline(),
                "commerce.catalog.federation.overall-deadline"
        );
        if (federationProperties.overallDeadline().compareTo(requiredOverallDeadline) < 0) {
            throw new IllegalStateException(
                    ("commerce.catalog.federation.overall-deadline must be at least %s to contain "
                            + "the enabled Shopify Global Catalog source timeout")
                            .formatted(requiredOverallDeadline)
            );
        }
    }

    private void requireNanosCapacity(Duration duration, String property) {
        if (duration == null) {
            throw new IllegalStateException(property + " is required");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    property + " exceeds nanosecond deadline capacity",
                    exception
            );
        }
    }
}
