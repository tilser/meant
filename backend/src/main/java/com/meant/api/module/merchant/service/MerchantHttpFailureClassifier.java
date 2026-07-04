package com.meant.api.module.merchant.service;

import org.springframework.web.client.RestClientResponseException;

final class MerchantHttpFailureClassifier {

    private static final int TOO_MANY_REQUESTS = 429;

    private MerchantHttpFailureClassifier() {
    }

    static boolean isRateLimited(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == TOO_MANY_REQUESTS) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
