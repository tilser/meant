package com.meant.api.plugin.checkout.common.exception;

public class UcpCheckoutResponseException extends RuntimeException {

    public UcpCheckoutResponseException(String message) {
        super(message);
    }

    public UcpCheckoutResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
