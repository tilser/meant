package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CommerceTransportFailure;
import com.meant.api.module.cart.properties.CartRetryProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Provider-neutral replay and reconciliation classification for one logical mutation. */
@Component
@RequiredArgsConstructor
public class CommerceMutationPolicy {
    private final CartRetryProperties properties;
    private final CartRetrySleeper sleeper;

    public boolean requiresReconciliation(Throwable throwable) {
        CommerceTransportFailure failure = failure(throwable);
        return failure != null && (failure.kind() == CommerceTransportFailure.Kind.TIMEOUT
                || failure.kind() == CommerceTransportFailure.Kind.SERVER_FAILURE);
    }

    public boolean prepareIdempotentRetry(Throwable throwable) {
        CommerceTransportFailure failure = failure(throwable);
        if (failure == null) {
            return false;
        }
        if (failure.kind() == CommerceTransportFailure.Kind.TIMEOUT
                || failure.kind() == CommerceTransportFailure.Kind.SERVER_FAILURE) {
            return true;
        }
        if (failure.kind() != CommerceTransportFailure.Kind.RATE_LIMITED) {
            return false;
        }
        Duration delay = failure.retryAfter().orElse(null);
        if (delay == null || delay.isNegative() || delay.compareTo(properties.maxReconciliationDelay()) > 0) {
            return false;
        }
        sleeper.sleep(delay);
        return true;
    }

    private CommerceTransportFailure failure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CommerceTransportFailure failure) {
                return failure;
            }
            current = current.getCause();
        }
        return null;
    }
}
