package com.meant.api.plugin.order.common.exception;

public class UcpOrderResponseException extends RuntimeException {

    public UcpOrderResponseException(String message) {
        super(message);
    }

    public UcpOrderResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
