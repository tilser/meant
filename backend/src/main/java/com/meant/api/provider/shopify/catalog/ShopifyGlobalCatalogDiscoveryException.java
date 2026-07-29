package com.meant.api.provider.shopify.catalog;

/** Raised when Shopify Global Catalog discovery cannot produce a safe compatible route. */
public class ShopifyGlobalCatalogDiscoveryException extends RuntimeException {

    private final boolean incompatible;

    private ShopifyGlobalCatalogDiscoveryException(String message, boolean incompatible, Throwable cause) {
        super(message, cause);
        this.incompatible = incompatible;
    }

    static ShopifyGlobalCatalogDiscoveryException incompatible(String message) {
        return new ShopifyGlobalCatalogDiscoveryException(message, true, null);
    }

    static ShopifyGlobalCatalogDiscoveryException incompatible(String message, Throwable cause) {
        return new ShopifyGlobalCatalogDiscoveryException(message, true, cause);
    }

    static ShopifyGlobalCatalogDiscoveryException transientFailure(String message, Throwable cause) {
        return new ShopifyGlobalCatalogDiscoveryException(message, false, cause);
    }

    public boolean incompatible() {
        return incompatible;
    }
}
