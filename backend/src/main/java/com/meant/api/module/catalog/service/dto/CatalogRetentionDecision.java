package com.meant.api.module.catalog.service.dto;

import java.time.Duration;

/** Executable retention decision issued by one reviewed provider policy version. */
public record CatalogRetentionDecision(String policyKey, CatalogRetentionMode mode, Duration maximumRetention) {

    public CatalogRetentionDecision {
        if (policyKey == null || policyKey.isBlank() || mode == null) {
            throw new IllegalArgumentException("Policy key and retention mode are required");
        }
        policyKey = policyKey.trim();
        if (mode == CatalogRetentionMode.BOUNDED_CACHE) {
            if (maximumRetention == null || maximumRetention.isZero() || maximumRetention.isNegative()) {
                throw new IllegalArgumentException("Bounded cache decisions require a positive retention duration");
            }
        } else if (maximumRetention != null) {
            throw new IllegalArgumentException("Only bounded cache decisions may define retention duration");
        }
    }

    public static CatalogRetentionDecision sessionOnly(String policyKey) {
        return new CatalogRetentionDecision(policyKey, CatalogRetentionMode.SESSION_ONLY, null);
    }

    public static CatalogRetentionDecision identifiersOnly(String policyKey) {
        return new CatalogRetentionDecision(policyKey, CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY, null);
    }

    public static CatalogRetentionDecision bounded(String policyKey, Duration maximumRetention) {
        return new CatalogRetentionDecision(policyKey, CatalogRetentionMode.BOUNDED_CACHE, maximumRetention);
    }
}
