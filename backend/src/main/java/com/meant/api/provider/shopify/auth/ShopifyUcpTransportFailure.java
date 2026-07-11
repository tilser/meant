package com.meant.api.provider.shopify.auth;

public enum ShopifyUcpTransportFailure {
    AUTHENTICATION,
    INVALID_REQUEST,
    RATE_LIMITED,
    TIMEOUT,
    TRANSIENT_UPSTREAM,
    MALFORMED_RESPONSE
}
