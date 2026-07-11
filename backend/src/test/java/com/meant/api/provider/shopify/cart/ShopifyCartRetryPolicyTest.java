package com.meant.api.provider.shopify.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meant.api.provider.shopify.auth.ShopifyUcpTransportException;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportFailure;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ShopifyCartRetryPolicyTest {
    private final ShopifyRetrySleeper sleeper = mock(ShopifyRetrySleeper.class);
    private final ShopifyCartRetryPolicy policy = new ShopifyCartRetryPolicy(
            new ShopifyCartProperties(Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(2),
                    Duration.ofSeconds(3), Duration.ofSeconds(2)), sleeper);

    @Test
    void rateLimitWaitIsHonoredOnlyWithinBound() {
        Duration retryAfter = Duration.ofMillis(900);
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.RATE_LIMITED, retryAfter, 429))).isTrue();
        verify(sleeper).sleep(retryAfter);
        assertThat(policy.prepare(failure(
                ShopifyUcpTransportFailure.RATE_LIMITED, Duration.ofSeconds(3), 429))).isFalse();
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.RATE_LIMITED, null, 429))).isFalse();
    }

    @Test
    void onlyTransientFailuresAreEligibleAndOnlyTheyMayRefreshExternalProfile() {
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.TIMEOUT, null, null))).isTrue();
        assertThat(policy.refreshExternalRoute(failure(
                ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM, null, 503))).isTrue();
        assertThat(policy.refreshExternalRoute(failure(
                ShopifyUcpTransportFailure.RATE_LIMITED, Duration.ofSeconds(1), 429))).isFalse();
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.AUTHENTICATION, null, 401))).isFalse();
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.INVALID_REQUEST, null, 404))).isFalse();
        assertThat(policy.prepare(failure(ShopifyUcpTransportFailure.MALFORMED_RESPONSE, null, null))).isFalse();
        verifyNoInteractions(sleeper);
    }

    private ShopifyUcpTransportException failure(
            ShopifyUcpTransportFailure type, Duration retryAfter, Integer status) {
        return new ShopifyUcpTransportException(type, "redacted", retryAfter, status, null);
    }
}
