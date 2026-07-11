package com.meant.api.provider.shopify.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.exception.CommerceTransportFailure;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ShopifyCommerceFailureMapperTest {

    @ParameterizedTest
    @MethodSource("failures")
    void classifiesEveryRequiredProtocolAndTransportFailure(
            ShopifyUcpTransportFailure transportFailure,
            Integer status,
            CommerceTransportFailure.Kind expected
    ) {
        ShopifyUcpTransportException exception = new ShopifyUcpTransportException(
                transportFailure, "redacted", Duration.ofSeconds(3), status, null);

        CommerceTransportFailure mapped = ShopifyCommerceFailureMapper.map(exception);

        assertThat(mapped.kind()).isEqualTo(expected);
        assertThat(mapped.retryAfter()).contains(Duration.ofSeconds(3));
        assertThat(mapped.toString()).doesNotContain("token").doesNotContain("payload");
    }

    private static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(ShopifyUcpTransportFailure.AUTHENTICATION, 401,
                        CommerceTransportFailure.Kind.UNAUTHORIZED),
                Arguments.of(ShopifyUcpTransportFailure.AUTHENTICATION, 403,
                        CommerceTransportFailure.Kind.FORBIDDEN),
                Arguments.of(ShopifyUcpTransportFailure.INVALID_REQUEST, 404,
                        CommerceTransportFailure.Kind.NOT_FOUND),
                Arguments.of(ShopifyUcpTransportFailure.INVALID_REQUEST, 409,
                        CommerceTransportFailure.Kind.CONFLICT),
                Arguments.of(ShopifyUcpTransportFailure.INVALID_REQUEST, 422,
                        CommerceTransportFailure.Kind.UNPROCESSABLE),
                Arguments.of(ShopifyUcpTransportFailure.RATE_LIMITED, 429,
                        CommerceTransportFailure.Kind.RATE_LIMITED),
                Arguments.of(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM, 503,
                        CommerceTransportFailure.Kind.SERVER_FAILURE),
                Arguments.of(ShopifyUcpTransportFailure.TIMEOUT, null,
                        CommerceTransportFailure.Kind.TIMEOUT),
                Arguments.of(ShopifyUcpTransportFailure.MALFORMED_RESPONSE, null,
                        CommerceTransportFailure.Kind.MALFORMED_RESPONSE)
        );
    }
}
