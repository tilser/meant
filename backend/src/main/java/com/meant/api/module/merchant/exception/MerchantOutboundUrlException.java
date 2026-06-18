package com.meant.api.module.merchant.exception;

public class MerchantOutboundUrlException extends RuntimeException {

    public MerchantOutboundUrlException(String message) {
        super(message);
    }

    public MerchantOutboundUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}
