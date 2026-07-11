package com.meant.api.provider.shopify.auth;

import com.meant.api.module.cart.exception.CommerceTransportFailure;

public final class ShopifyCommerceFailureMapper {
    private ShopifyCommerceFailureMapper() {
    }

    public static CommerceTransportFailure map(ShopifyUcpTransportException exception) {
        int status = exception.upstreamStatus().orElse(0);
        CommerceTransportFailure.Kind kind = switch (status) {
            case 401 -> CommerceTransportFailure.Kind.UNAUTHORIZED;
            case 403 -> CommerceTransportFailure.Kind.FORBIDDEN;
            case 404 -> CommerceTransportFailure.Kind.NOT_FOUND;
            case 409 -> CommerceTransportFailure.Kind.CONFLICT;
            case 422 -> CommerceTransportFailure.Kind.UNPROCESSABLE;
            case 429 -> CommerceTransportFailure.Kind.RATE_LIMITED;
            default -> switch (exception.failure()) {
                case AUTHENTICATION -> CommerceTransportFailure.Kind.UNAUTHORIZED;
                case RATE_LIMITED -> CommerceTransportFailure.Kind.RATE_LIMITED;
                case TIMEOUT -> CommerceTransportFailure.Kind.TIMEOUT;
                case TRANSIENT_UPSTREAM -> CommerceTransportFailure.Kind.SERVER_FAILURE;
                case MALFORMED_RESPONSE -> CommerceTransportFailure.Kind.MALFORMED_RESPONSE;
                case INVALID_REQUEST -> CommerceTransportFailure.Kind.INVALID_REQUEST;
            };
        };
        return new CommerceTransportFailure(kind, exception.retryAfter().orElse(null), exception);
    }
}
