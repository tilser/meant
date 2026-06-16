package com.meant.api.module.merchant.exception;

public class MerchantCartException extends RuntimeException {

    public MerchantCartException(String message) {
        super(message);
    }

    public MerchantCartException(String message, Throwable cause) {
        super(message, cause);
    }
}
