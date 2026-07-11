package com.meant.api.module.catalog.service.dto;

import java.time.Duration;

/** Atomic admission decision for one durable search record. */
public record CatalogSearchRetentionAdmission(
        boolean admitted,
        Duration maximumRetention,
        String policyFingerprint,
        CatalogRetentionMode rejectionMode
) {

    public CatalogSearchRetentionAdmission {
        if (admitted && (maximumRetention == null || policyFingerprint == null || policyFingerprint.isBlank())) {
            throw new IllegalArgumentException("Admitted searches require retention and a policy fingerprint");
        }
        if (!admitted && rejectionMode == null) {
            throw new IllegalArgumentException("Rejected searches require a retention mode");
        }
    }

    public static CatalogSearchRetentionAdmission rejected(CatalogRetentionMode mode) {
        return new CatalogSearchRetentionAdmission(false, null, null, mode);
    }

    public static CatalogSearchRetentionAdmission admitted(Duration retention, String fingerprint) {
        return new CatalogSearchRetentionAdmission(true, retention, fingerprint, null);
    }
}
