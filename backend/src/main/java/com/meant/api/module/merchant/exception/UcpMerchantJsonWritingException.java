package com.meant.api.module.merchant.exception;

public class UcpMerchantJsonWritingException extends RuntimeException {

    public UcpMerchantJsonWritingException(Throwable cause) {
        super("Unable to write UCP merchant JSON", cause);
    }
}
