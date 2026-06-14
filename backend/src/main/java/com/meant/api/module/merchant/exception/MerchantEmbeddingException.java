package com.meant.api.module.merchant.exception;

public class MerchantEmbeddingException extends RuntimeException {

    public MerchantEmbeddingException(String message) {
        super(message);
    }

    public MerchantEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
