package com.meant.api.module.merchant.exception;

public class MerchantCatalogSearchException extends RuntimeException {

    public MerchantCatalogSearchException(String message) {
        super(message);
    }

    public MerchantCatalogSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
