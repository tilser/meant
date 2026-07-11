package com.meant.api.provider.shopify.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProviderType;
import org.junit.jupiter.api.Test;

class ShopifyReviewProductIdNormalizationStrategyTest {

    private final ShopifyReviewProductIdNormalizationStrategy strategy =
            new ShopifyReviewProductIdNormalizationStrategy();

    @Test
    void normalizesShopifyProductGidWithoutOwningGenericReviewVendorBehavior() {
        assertThat(strategy.supports("gid://shopify/Product/123?market=US")).isTrue();
        assertThat(strategy.normalize("gid://shopify/Product/123/?market=US")).isEqualTo("123");
        assertThat(strategy.supports("123")).isFalse();
    }

    @Test
    void contributesOnlyShopifyAppProtocolEvidence() {
        ShopifyReviewProviderEvidenceContributor contributor = new ShopifyReviewProviderEvidenceContributor();

        assertThat(contributor.evidencePatterns(ReviewProviderType.OKENDO))
                .containsExactly("shopify://apps/okendo");
        assertThat(contributor.evidencePatterns(ReviewProviderType.YOTPO)).isEmpty();
    }
}
