package com.meant.api.provider.shopify.capability;

public enum ShopifyAuthorizationTier {
    NONE(0),
    STANDARD(1),
    TOKEN(2);

    private final int level;

    ShopifyAuthorizationTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
