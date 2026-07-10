package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.transport.dto.ShopifyTokenMetadata;

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
