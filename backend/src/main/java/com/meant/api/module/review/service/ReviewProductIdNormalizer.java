package com.meant.api.module.review.service;

import org.springframework.stereotype.Service;

@Service
public class ReviewProductIdNormalizer {

    private static final String SHOPIFY_PRODUCT_GID_PREFIX = "gid://shopify/Product/";

    public String normalize(String productId) {
        if (productId == null) {
            return null;
        }
        String trimmed = productId.trim();
        if (!trimmed.startsWith(SHOPIFY_PRODUCT_GID_PREFIX)) {
            return trimTrailingSlashes(trimmed);
        }
        String numericId = trimmed.substring(SHOPIFY_PRODUCT_GID_PREFIX.length());
        int queryStart = numericId.indexOf('?');
        if (queryStart >= 0) {
            numericId = numericId.substring(0, queryStart);
        }
        return trimTrailingSlashes(numericId);
    }

    private String trimTrailingSlashes(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
