package com.meant.api.module.merchant.service;

import org.springframework.web.client.RestClientResponseException;

final class MerchantHttpFailureClassifier {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_CAUSE_DEPTH = 50;

    private MerchantHttpFailureClassifier() {
    }

    static boolean isRateLimited(Throwable exception) {
        Throwable current = exception;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == TOO_MANY_REQUESTS) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }
}
