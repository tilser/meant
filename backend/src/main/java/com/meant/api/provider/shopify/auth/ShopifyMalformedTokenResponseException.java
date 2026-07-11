package com.meant.api.provider.shopify.auth;

public class ShopifyMalformedTokenResponseException extends ShopifyTokenClientException {

    public ShopifyMalformedTokenResponseException(String message) {
        super(message);
    }
}
