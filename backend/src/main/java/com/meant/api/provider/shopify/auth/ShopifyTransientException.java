package com.meant.api.provider.shopify.auth;

public class ShopifyTransientException extends ShopifyTokenClientException {

    public ShopifyTransientException(String message) {
        super(message);
    }
}
