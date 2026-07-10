package com.meant.api.plugin.transport.client;

import java.time.Duration;
import java.util.Optional;

public class ShopifyRateLimitException extends ShopifyTokenClientException {

    private final Duration retryAfter;

    public ShopifyRateLimitException(Duration retryAfter) {
        super("Shopify token endpoint rate limited the request");
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
