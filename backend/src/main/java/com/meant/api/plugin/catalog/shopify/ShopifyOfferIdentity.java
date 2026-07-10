package com.meant.api.plugin.catalog.shopify;

/**
 * Stable Shopify offer-key identity shared by Global Catalog and Storefront observations.
 *
 * <p>Shopify's published Global Catalog response exposes the immutable ProductVariant GID and UPID
 * group, but not the seller's Product GID. A storefront exposes Product and ProductVariant GIDs.
 * Using a versioned, variant-derived product anchor for offer identity makes the same commercial
 * variant converge across both paths. The original provider product/UPID remains intact in
 * {@code ResultProvenance}; this anchor is used only for collision-safe offer-key construction.
 */
public final class ShopifyOfferIdentity {

    private static final String VARIANT_PRODUCT_ANCHOR = "shopify-variant-product:v1:";

    private ShopifyOfferIdentity() {
    }

    public static String productAnchor(String externalProductId, String externalVariantId) {
        if (externalVariantId != null && !externalVariantId.isBlank()) {
            return VARIANT_PRODUCT_ANCHOR + externalVariantId.trim();
        }
        if (externalProductId == null || externalProductId.isBlank()) {
            throw new IllegalArgumentException("Shopify offer identity needs a product or variant identifier");
        }
        return externalProductId.trim();
    }
}
