package com.meant.api.module.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewProviderTest {

    @Test
    void retryableFailurePreservesLastKnownProviderConfiguration() {
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        ReviewProvider provider = ReviewProvider.builder()
                .merchantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .merchantDomain("merchant.example")
                .provider(ReviewProviderType.KLAVIYO)
                .status(ReviewProviderStatus.DETECTED)
                .providerKey("company-1")
                .productIdType(ReviewProductIdType.SHOPIFY_NUMERIC_ID)
                .sourceUrl("https://merchant.example/products/tee")
                .evidence("klaviyo")
                .createdAt(now)
                .updatedAt(now)
                .build();

        provider.markRetryableFailure(
                "merchant.example",
                "Storefront temporarily unavailable",
                now.plusSeconds(60),
                now.plusSeconds(600)
        );

        assertThat(provider.getStatus()).isEqualTo(ReviewProviderStatus.FAILED_RETRYABLE);
        assertThat(provider.getProvider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(provider.getProviderKey()).isEqualTo("company-1");
        assertThat(provider.getProductIdType()).isEqualTo(ReviewProductIdType.SHOPIFY_NUMERIC_ID);
        assertThat(provider.getSourceUrl()).isEqualTo("https://merchant.example/products/tee");
        assertThat(provider.getEvidence()).isEqualTo("klaviyo");
        assertThat(provider.getErrorMessage()).isEqualTo("Storefront temporarily unavailable");
    }
}
