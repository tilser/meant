package com.meant.api.provider.shopify.cart;

import com.meant.api.provider.shopify.auth.ShopifyUcpTransportException;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportFailure;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** One bounded retry for safe reads/idempotent cancellations; never retries authorization or bad payloads. */
@Component
public class ShopifyCartRetryPolicy {
    private final Duration maxRetryDelay;
    private final ShopifyRetrySleeper sleeper;

    public ShopifyCartRetryPolicy(ShopifyCartProperties properties, ShopifyRetrySleeper sleeper) {
        this.maxRetryDelay = properties.maxRetryDelay();
        this.sleeper = sleeper;
    }

    public boolean prepare(RuntimeException failure) {
        if (!(failure instanceof ShopifyUcpTransportException transport)) {
            return false;
        }
        if (transport.failure() != ShopifyUcpTransportFailure.TIMEOUT
                && transport.failure() != ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM
                && transport.failure() != ShopifyUcpTransportFailure.RATE_LIMITED) {
            return false;
        }
        Duration delay = transport.retryAfter().orElse(Duration.ZERO);
        if (transport.failure() == ShopifyUcpTransportFailure.RATE_LIMITED && delay.isZero()) {
            return false;
        }
        if (delay.compareTo(maxRetryDelay) > 0) {
            return false;
        }
        if (!delay.isZero()) {
            sleeper.sleep(delay);
        }
        return true;
    }

    public boolean refreshExternalRoute(RuntimeException failure) {
        return failure instanceof ShopifyUcpTransportException transport
                && (transport.failure() == ShopifyUcpTransportFailure.TIMEOUT
                || transport.failure() == ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM);
    }
}
