package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meant.api.module.cart.exception.CommerceTransportFailure;
import com.meant.api.module.cart.properties.CartRetryProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CommerceMutationPolicyTest {
    private final CartRetrySleeper sleeper = mock(CartRetrySleeper.class);
    private final CommerceMutationPolicy policy = new CommerceMutationPolicy(
            new CartRetryProperties(Duration.ofSeconds(2)), sleeper);

    @Test
    void reconcilesOnlyUnknownMutationOutcomes() {
        assertThat(policy.requiresReconciliation(failure(CommerceTransportFailure.Kind.TIMEOUT, null))).isTrue();
        assertThat(policy.requiresReconciliation(failure(CommerceTransportFailure.Kind.SERVER_FAILURE, null))).isTrue();
        for (CommerceTransportFailure.Kind kind : CommerceTransportFailure.Kind.values()) {
            if (kind != CommerceTransportFailure.Kind.TIMEOUT
                    && kind != CommerceTransportFailure.Kind.SERVER_FAILURE) {
                assertThat(policy.requiresReconciliation(failure(kind, null))).isFalse();
            }
        }
    }

    @Test
    void honorsBoundedRetryAfterWithoutSleepingInTheTest() {
        Duration accepted = Duration.ofMillis(750);
        assertThat(policy.prepareIdempotentRetry(
                failure(CommerceTransportFailure.Kind.RATE_LIMITED, accepted))).isTrue();
        verify(sleeper).sleep(accepted);

        assertThat(policy.prepareIdempotentRetry(
                failure(CommerceTransportFailure.Kind.RATE_LIMITED, Duration.ofSeconds(3)))).isFalse();
        assertThat(policy.prepareIdempotentRetry(
                failure(CommerceTransportFailure.Kind.RATE_LIMITED, null))).isFalse();
    }

    @Test
    void neverRetriesAuthorizationNotFoundForbiddenMalformedOrInvalidFailures() {
        assertThat(policy.prepareIdempotentRetry(failure(CommerceTransportFailure.Kind.UNAUTHORIZED, null))).isFalse();
        assertThat(policy.prepareIdempotentRetry(failure(CommerceTransportFailure.Kind.FORBIDDEN, null))).isFalse();
        assertThat(policy.prepareIdempotentRetry(failure(CommerceTransportFailure.Kind.NOT_FOUND, null))).isFalse();
        assertThat(policy.prepareIdempotentRetry(
                failure(CommerceTransportFailure.Kind.MALFORMED_RESPONSE, null))).isFalse();
        assertThat(policy.prepareIdempotentRetry(
                failure(CommerceTransportFailure.Kind.INVALID_REQUEST, null))).isFalse();
        verifyNoInteractions(sleeper);
    }

    private CommerceTransportFailure failure(CommerceTransportFailure.Kind kind, Duration retryAfter) {
        return new CommerceTransportFailure(kind, retryAfter, null);
    }
}
