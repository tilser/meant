package com.meant.api.provider.shopify.auth;

public class ShopifyAuthenticationException extends ShopifyTokenClientException {

    public ShopifyAuthenticationException(String message) {
        super(message);
    }
}
