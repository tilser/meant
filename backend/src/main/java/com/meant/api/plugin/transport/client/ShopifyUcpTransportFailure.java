package com.meant.api.plugin.transport.client;

public enum ShopifyUcpTransportFailure {
    AUTHENTICATION,
    INVALID_REQUEST,
    RATE_LIMITED,
    TIMEOUT,
    TRANSIENT_UPSTREAM,
    UNAVAILABLE,
    MALFORMED_RESPONSE
}
