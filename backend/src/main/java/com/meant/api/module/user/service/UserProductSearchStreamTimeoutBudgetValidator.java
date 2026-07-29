package com.meant.api.module.user.service;

import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Prevents the public product-search stream from expiring before federation can finish safely. */
@Component
@RequiredArgsConstructor
public class UserProductSearchStreamTimeoutBudgetValidator {

    static final Duration TERMINAL_EVENT_DELIVERY_RESERVE = Duration.ofSeconds(1);

    private final UserProductSearchProperties searchProperties;
    private final FederatedCatalogDiscoveryProperties federationProperties;

    @PostConstruct
    void validate() {
        Duration streamTimeout = searchProperties.streamTimeout();
        Duration federationDeadline = federationProperties.overallDeadline();
        requirePositiveNanosCapacity(streamTimeout, "user.product-search.stream-timeout");
        requirePositiveNanosCapacity(
                federationDeadline,
                "commerce.catalog.federation.overall-deadline"
        );
        Duration requiredStreamTimeout;
        try {
            requiredStreamTimeout = federationDeadline.plus(TERMINAL_EVENT_DELIVERY_RESERVE);
            requiredStreamTimeout.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Computed product-search stream budget exceeds nanosecond deadline capacity",
                    exception
            );
        }
        if (streamTimeout.compareTo(requiredStreamTimeout) < 0) {
            throw new IllegalStateException(
                    "user.product-search.stream-timeout must be at least %s to contain "
                            .formatted(requiredStreamTimeout)
                            + "the complete catalog federation deadline and terminal-event delivery reserve"
            );
        }
    }

    private void requirePositiveNanosCapacity(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(property + " must be positive");
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
