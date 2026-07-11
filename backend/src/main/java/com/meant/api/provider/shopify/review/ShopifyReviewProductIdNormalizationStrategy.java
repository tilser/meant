package com.meant.api.provider.shopify.review;

import com.meant.api.module.review.service.port.ReviewProductIdNormalizationStrategy;
import org.springframework.stereotype.Component;

@Component
public class ShopifyReviewProductIdNormalizationStrategy implements ReviewProductIdNormalizationStrategy {

    private static final String PRODUCT_GID_PREFIX = "gid://shopify/Product/";

    @Override
    public boolean supports(String productId) {
        return productId != null && productId.trim().startsWith(PRODUCT_GID_PREFIX);
    }

    @Override
    public String normalize(String productId) {
        String numericId = productId.trim().substring(PRODUCT_GID_PREFIX.length());
        int queryStart = numericId.indexOf('?');
        if (queryStart >= 0) {
            numericId = numericId.substring(0, queryStart);
        }
        return trimTrailingSlashes(numericId);
    }

    private String trimTrailingSlashes(String value) {
        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
