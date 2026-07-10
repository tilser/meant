package com.meant.api.plugin.transport.client;

public enum ShopifyUcpTransportFailure {
    AUTHENTICATION,
    INVALID_REQUEST,
    RATE_LIMITED,
    TIMEOUT,
    TRANSIENT_UPSTREAM,
    MALFORMED_RESPONSE
}
