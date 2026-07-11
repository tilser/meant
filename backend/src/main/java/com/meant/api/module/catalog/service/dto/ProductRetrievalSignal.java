package com.meant.api.module.catalog.service.dto;

import java.util.regex.Pattern;

/** Provider-adapter calibrated retrieval evidence; raw source scores never cross this boundary. */
public record ProductRetrievalSignal(
        DiscoverySourceIdentity source,
        OfferMerchantScope merchantScope,
        Feature feature,
        int valueBasisPoints,
        String calibrationVersion
) {

    private static final Pattern VERSION = Pattern.compile("[a-zA-Z0-9._-]{1,64}");

    public ProductRetrievalSignal {
        if (source == null || feature == null) {
            throw new IllegalArgumentException("Retrieval signal source and feature are required");
        }
        if (valueBasisPoints < 0 || valueBasisPoints > 10_000) {
            throw new IllegalArgumentException("Retrieval signal must be between 0 and 10000 basis points");
        }
        if (calibrationVersion == null || !VERSION.matcher(calibrationVersion).matches()) {
            throw new IllegalArgumentException("Retrieval signal calibration version must be a bounded identifier");
        }
        calibrationVersion = calibrationVersion.trim();
    }

    public ProductRetrievalSignal(
            DiscoverySourceIdentity source,
            Feature feature,
            int valueBasisPoints,
            String calibrationVersion
    ) {
        this(source, null, feature, valueBasisPoints, calibrationVersion);
    }

    public enum Feature {
        INTENT_FIT
    }
}
