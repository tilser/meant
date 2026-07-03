package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import org.junit.jupiter.api.Test;

class ReviewProviderDetectionServiceTest {

    private final ReviewProviderDetectionService detectionService = new ReviewProviderDetectionService();

    @Test
    void extractsKlaviyoCompanyIdFromStaticScriptWhenReviewMarkersExist() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://the-shirt.com/products/the-jet-set-icon-shirt",
                """
                        <script async src="https://static.klaviyo.com/onsite/js/J5feSG/klaviyo.js?company_id=J5feSG"></script>
                        <div class="klaviyo_reviews_product_reviews"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(result.status()).isEqualTo(ReviewProviderStatus.DETECTED);
        assertThat(result.providerKey()).isEqualTo("J5feSG");
        assertThat(result.evidence()).contains("klaviyo_reviews");
    }

    @Test
    void detectsReviewSpecificMarkers() {
        ReviewProviderDetectionResult klReviews = detectionService.detect(
                "https://merchant.example/products/a",
                """
                        <script>klaviyo.init({ account: "J5feSG" })</script>
                        <div id="kl_reviews"></div>
                        """
        );
        ReviewProviderDetectionResult metafieldReviews = detectionService.detect(
                "https://merchant.example/products/b",
                """
                        <script>{"accountID":"J5feSG"}</script>
                        <script>window.MetafieldReviews = [];</script>
                        """
        );

        assertThat(klReviews.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(metafieldReviews.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
    }

    @Test
    void doesNotClassifyGenericKlaviyoMarketingOnlyHtmlAsKlaviyoReviews() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://merchant.example",
                """
                        <script async src="https://static.klaviyo.com/onsite/js/J5feSG/klaviyo.js?company_id=J5feSG"></script>
                        <form data-klaviyo-list-id="abc123"></form>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.NONE);
        assertThat(result.status()).isEqualTo(ReviewProviderStatus.NOT_FOUND);
    }
}
