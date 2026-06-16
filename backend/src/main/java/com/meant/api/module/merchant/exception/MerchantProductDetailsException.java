package com.meant.api.module.merchant.exception;

public class MerchantProductDetailsException extends RuntimeException {

    public MerchantProductDetailsException(String message) {
        super(message);
    }

    public MerchantProductDetailsException(String message, Throwable cause) {
        super(message, cause);
    }
}
