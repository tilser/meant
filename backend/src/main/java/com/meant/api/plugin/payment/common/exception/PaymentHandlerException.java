package com.meant.api.plugin.payment.common.exception;

public class PaymentHandlerException extends RuntimeException {

    public PaymentHandlerException(String message) {
        super(message);
    }

    public PaymentHandlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
