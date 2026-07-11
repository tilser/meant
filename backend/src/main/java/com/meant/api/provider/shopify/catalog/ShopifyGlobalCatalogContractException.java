package com.meant.api.provider.shopify.catalog;

public class ShopifyGlobalCatalogContractException extends RuntimeException {

    public ShopifyGlobalCatalogContractException(String message) {
        super(message);
    }

    public ShopifyGlobalCatalogContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
