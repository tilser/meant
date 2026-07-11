package com.meant.api.module.checkout.exception;

public class CheckoutSafetyException extends RuntimeException {

    public CheckoutSafetyException(String message) {
        super(message);
    }

    public CheckoutSafetyException(String message, Throwable cause) {
        super(message, cause);
    }
}
