package com.meant.api.provider.shopify.auth;

import java.time.Duration;
import java.util.Optional;

public class ShopifyUcpTransportException extends RuntimeException {

    private final ShopifyUcpTransportFailure failure;
    private final Duration retryAfter;
    private final Integer upstreamStatus;

    public ShopifyUcpTransportException(
            ShopifyUcpTransportFailure failure,
            String safeMessage,
            Duration retryAfter,
            Integer upstreamStatus,
            Throwable cause
    ) {
        super(safeMessage, cause);
        this.failure = failure;
        this.retryAfter = retryAfter;
        this.upstreamStatus = upstreamStatus;
    }

    public ShopifyUcpTransportFailure failure() {
        return failure;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public Optional<Integer> upstreamStatus() {
        return Optional.ofNullable(upstreamStatus);
    }
}
