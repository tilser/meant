package com.meant.api.plugin.checkout.common.exception;

public class UcpCheckoutSafetyException extends RuntimeException {

    public UcpCheckoutSafetyException(String message) {
        super(message);
    }

    public UcpCheckoutSafetyException(String message, Throwable cause) {
        super(message, cause);
    }
}
