package com.meant.api.module.cart.exception;

import java.time.Duration;
import java.util.Optional;

/** Redacted provider-neutral classification for cart and checkout transport failures. */
public class CommerceTransportFailure extends RuntimeException {
    public enum Kind {
        UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, UNPROCESSABLE,
        RATE_LIMITED, SERVER_FAILURE, TIMEOUT, MALFORMED_RESPONSE, INVALID_REQUEST
    }

    private final Kind kind;
    private final Duration retryAfter;

    public CommerceTransportFailure(Kind kind, Duration retryAfter, Throwable cause) {
        super("Commerce provider transport failed: " + kind.name().toLowerCase(), cause);
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public Kind kind() {
        return kind;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
