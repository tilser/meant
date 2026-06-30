package com.meant.api.plugin.payment.common.exception;

public class DuplicatePaymentHandlerException extends PaymentHandlerException {

    public DuplicatePaymentHandlerException(String name, String firstHandlerId, String secondHandlerId) {
        super("Duplicate payment handler name " + name + " for " + firstHandlerId + " and " + secondHandlerId);
    }
}
