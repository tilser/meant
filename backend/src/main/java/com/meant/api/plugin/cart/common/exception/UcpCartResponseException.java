package com.meant.api.plugin.cart.common.exception;

public class UcpCartResponseException extends RuntimeException {

    public UcpCartResponseException(String message) {
        super(message);
    }

    public UcpCartResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
