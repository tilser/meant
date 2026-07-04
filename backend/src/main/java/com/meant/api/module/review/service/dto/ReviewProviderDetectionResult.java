package com.meant.api.module.review.service.dto;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;

public record ReviewProviderDetectionResult(
        ReviewProviderType provider,
        ReviewProviderStatus status,
        String providerKey,
        ReviewProductIdType productIdType,
        String sourceUrl,
        String evidence
) {

    public static ReviewProviderDetectionResult klaviyo(
            String providerKey,
            String sourceUrl,
            String evidence
    ) {
        return new ReviewProviderDetectionResult(
                ReviewProviderType.KLAVIYO,
                ReviewProviderStatus.DETECTED,
                providerKey,
                ReviewProductIdType.SHOPIFY_NUMERIC_ID,
                sourceUrl,
                evidence
        );
    }

    public static ReviewProviderDetectionResult yotpo(
            String providerKey,
            String sourceUrl,
            String evidence
    ) {
        return new ReviewProviderDetectionResult(
                ReviewProviderType.YOTPO,
                ReviewProviderStatus.DETECTED,
                providerKey,
                ReviewProductIdType.SHOPIFY_NUMERIC_ID,
                sourceUrl,
                evidence
        );
    }

    public static ReviewProviderDetectionResult notFound() {
        return new ReviewProviderDetectionResult(
                ReviewProviderType.NONE,
                ReviewProviderStatus.NOT_FOUND,
                null,
                null,
                null,
                null
        );
    }

    public boolean detected() {
        return status == ReviewProviderStatus.DETECTED;
    }
}
