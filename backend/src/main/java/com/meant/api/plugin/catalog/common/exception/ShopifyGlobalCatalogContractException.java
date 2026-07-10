package com.meant.api.plugin.catalog.common.exception;

public class ShopifyGlobalCatalogContractException extends RuntimeException {

    public ShopifyGlobalCatalogContractException(String message) {
        super(message);
    }

    public ShopifyGlobalCatalogContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
