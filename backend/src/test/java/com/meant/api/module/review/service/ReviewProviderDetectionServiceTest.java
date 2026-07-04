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
        ReviewProviderDetectionResult clientReviews = detectionService.detect(
                "https://merchant.example/products/b",
                """
                        <script>{"accountID":"J5feSG"}</script>
                        <div data-route="reviews/api/client_reviews"></div>
                        """
        );

        assertThat(klReviews.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(clientReviews.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
    }

    @Test
    void detectsYotpoBeforeKlaviyoMarketing() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://culturekings.com/products/tee",
                """
                        <script async src="https://static.klaviyo.com/onsite/js/RYyrrE/klaviyo.js?company_id=RYyrrE"></script>
                        <script>
                          var MetafieldReviews = {};
                          var MetafieldYotpoRating = "5.0";
                          var MetafieldYotpoCount = "4";
                          window.klaviyoReviewsProductDesignMode = false;
                        </script>
                        <script type="text/plain">
                          (function e(){var e=document.createElement("script");e.src="//staticw2.yotpo.com/BbbH23pfMsuacT2NMxfTdJSEECWZEUxUlY5kyl5t/widget.js";})();
                        </script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.YOTPO);
        assertThat(result.status()).isEqualTo(ReviewProviderStatus.DETECTED);
        assertThat(result.providerKey()).isEqualTo("BbbH23pfMsuacT2NMxfTdJSEECWZEUxUlY5kyl5t");
        assertThat(result.evidence()).contains("staticw2.yotpo.com");
    }

    @Test
    void detectsYotpoWidgetRepositoryLoader() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.spigen.com/products/macbook-air-series-thin-fit",
                """
                        <script type="text/javascript"
                          src="https://cdn-widgetsrepository.yotpo.com/v1/loader/ySssVow4bkeHaw3pnMFNoguTDbxb1DlsLc9cV6pD?languageCode=en"
                          async></script>
                        <div class="yotpo-widget-instance"
                          data-yotpo-instance-id="539171"
                          data-yotpo-product-id="7406592065583"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.YOTPO);
        assertThat(result.status()).isEqualTo(ReviewProviderStatus.DETECTED);
        assertThat(result.providerKey()).isEqualTo("ySssVow4bkeHaw3pnMFNoguTDbxb1DlsLc9cV6pD");
        assertThat(result.evidence()).contains("data-yotpo-product-id");
    }

    @Test
    void doesNotClassifyGenericKlaviyoOnsiteReviewTrackingAsKlaviyoReviews() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://merchant.example",
                """
                        <script async src="https://static.klaviyo.com/onsite/js/RYyrrE/klaviyo.js?company_id=RYyrrE"></script>
                        <script>
                          var MetafieldReviews = {};
                          window.klaviyoReviewsProductDesignMode = false;
                        </script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.NONE);
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
