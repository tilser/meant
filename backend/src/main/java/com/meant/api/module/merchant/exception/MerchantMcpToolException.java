package com.meant.api.module.merchant.exception;

public class MerchantMcpToolException extends RuntimeException {

    public MerchantMcpToolException(String message) {
        super(message);
    }

    public MerchantMcpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
