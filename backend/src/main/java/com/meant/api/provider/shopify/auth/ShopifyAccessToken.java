package com.meant.api.provider.shopify.auth;

import com.meant.api.provider.shopify.auth.ShopifyTokenMetadata;

record ShopifyAccessToken(
        String value,
        long generation,
        ShopifyTokenMetadata metadata
) {

    @Override
    public String toString() {
        return "ShopifyAccessToken[value=[redacted], generation=%d, metadata=%s]"
                .formatted(generation, metadata);
    }
}
