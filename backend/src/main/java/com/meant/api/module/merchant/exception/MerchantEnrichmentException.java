package com.meant.api.module.merchant.exception;

public class MerchantEnrichmentException extends RuntimeException {

    public MerchantEnrichmentException(String message) {
        super(message);
    }

    public MerchantEnrichmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
