package com.meant.api.plugin.payment.common.exception;

public class UnknownPaymentHandlerException extends PaymentHandlerException {

    public UnknownPaymentHandlerException(String name) {
        super("Unknown payment handler: " + name);
    }
}
