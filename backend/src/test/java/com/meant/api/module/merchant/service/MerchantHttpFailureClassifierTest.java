package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class MerchantHttpFailureClassifierTest {

    @Test
    void detectsRateLimitedExceptionInCauseChain() {
        RuntimeException exception = new RuntimeException(
                "wrapped",
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate limited", null, null, null)
        );

        assertThat(MerchantHttpFailureClassifier.isRateLimited(exception)).isTrue();
    }

    @Test
    void stopsTraversingExceptionCauseChainAfterDepthLimit() {
        Throwable rateLimited = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "rate limited",
                null,
                null,
                null
        );
        Throwable exception = rateLimited;
        for (int depth = 0; depth < 50; depth++) {
            exception = new RuntimeException("wrapper-" + depth, exception);
        }

        assertThat(MerchantHttpFailureClassifier.isRateLimited(exception)).isFalse();
    }
}
