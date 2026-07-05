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
    void detectsYotpoWidgetMarkupWithoutPublicProviderKey() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.allbirds.com/products/mens-runner",
                """
                        <div class="yotpo-widget-instance"
                          data-yotpo-instance-id="773533"
                          data-yotpo-product-id="7205207343184"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.YOTPO);
        assertThat(result.status()).isEqualTo(ReviewProviderStatus.DETECTED);
        assertThat(result.providerKey()).isNull();
        assertThat(result.evidence()).contains("data-yotpo-instance-id");
    }

    @Test
    void detectsOkendoReviewsSettings() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://gb.harrys.com/en/products/hydrating-face-wash",
                """
                        <script id="oke-reviews-settings" type="application/json">
                          {"subscriberId":"ac3ddecd-d40f-41bb-8e17-3a68e331cc08"}
                        </script>
                        <div data-oke-reviews-product-id="gid://shopify/Product/123"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.OKENDO);
        assertThat(result.providerKey()).isEqualTo("ac3ddecd-d40f-41bb-8e17-3a68e331cc08");
        assertThat(result.evidence()).contains("oke-reviews-settings");
    }

    @Test
    void detectsJudgeMeAppBlock() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.gouletpens.com/products/col-o-ring-ink-testing-book",
                """
                        <!-- BEGIN app block: shopify://apps/judge-me-reviews/blocks/judgeme_core/61ccd3b1-a9f2-4160-9fe9-4fec8413e5d8 -->
                        <link rel="dns-prefetch" href="https://cdn.judge.me">
                        <script class="jdgm-settings-script">window.jdgmSettings={}</script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.JUDGE_ME);
        assertThat(result.providerKey()).isNull();
        assertThat(result.evidence()).contains("judge-me-reviews");
    }

    @Test
    void detectsBazaarvoiceEscapedDeploymentScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://us.checkout.gymshark.com/products/shorts",
                """
                        <script>
                          window.__bv = "https:\\/\\/apps.bazaarvoice.com\\/deployments\\/gymshark\\/main_site\\/production\\/en_US\\/bv.js";
                        </script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.BAZAARVOICE);
        assertThat(result.providerKey()).isEqualTo("gymshark");
        assertThat(result.evidence()).contains("bazaarvoice.com");
    }

    @Test
    void detectsStampedWidgetScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.tentree.com/products/magnolia-shirt",
                """
                        <script src="https://cdn1.stamped.io/files/widget.min.js?shop=tentree.myshopify.com"></script>
                        <span class="stamped-badge" data-id="123"></span>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.STAMPED);
        assertThat(result.providerKey()).isEqualTo("tentree.myshopify.com");
        assertThat(result.evidence()).contains("stamped.io");
    }

    @Test
    void detectsReviewsIoWidget() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://thepihut.com/products/raspberry-pi-5",
                """
                        <script src="https://widget.reviews.co.uk/rating-snippet/dist.js"></script>
                        <script>
                          new ReviewsWidget('#ReviewsWidget', {
                            store: 'the-pi-hut',
                            widget: 'polaris'
                          });
                        </script>
                        <div class="ruk_rating_snippet" data-sku="raspberry-pi-5"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.REVIEWS_IO);
        assertThat(result.providerKey()).isEqualTo("the-pi-hut");
        assertThat(result.evidence()).contains("widget.reviews.co.uk");
    }

    @Test
    void detectsLooxWidgetScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://loreta.com.au/products/emerald-gemstone-dress",
                """
                        <script src="https://loox.io/widget/EypXuB-sm/loox.1506302220853.js?shop=clothing-loreta.myshopify.com"></script>
                        <div id="looxReviews"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.LOOX);
        assertThat(result.providerKey()).isEqualTo("EypXuB-sm");
        assertThat(result.evidence()).contains("loox.io");
    }

    @Test
    void detectsJunipStoreKey() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://partakefoods.com/products/soft-baked-lemon",
                """
                        <span class="junip-store-key"
                          data-store-key="T9ET8nZvHWsoce5FD1PC5Z5v"
                          data-review-count-enabled="true"></span>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.JUNIP);
        assertThat(result.providerKey()).isEqualTo("T9ET8nZvHWsoce5FD1PC5Z5v");
        assertThat(result.evidence()).contains("junip-store-key");
    }

    @Test
    void detectsPowerReviewsScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.morphe.com/products/brush-set",
                """
                        <script src="https://ui.powerreviews.com/stable/4.1/ui.js" defer></script>
                        <div id="product-power-reviews"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.POWER_REVIEWS);
        assertThat(result.evidence()).contains("powerreviews.com");
    }

    @Test
    void detectsTrustpilotEcommerceWidget() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://teefury.com/products/tee",
                """
                        <script src="https://ecommplugins-scripts.trustpilot.com/v2.1/js/header.min.js?settings=abc"></script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.TRUSTPILOT);
        assertThat(result.evidence()).contains("trustpilot.com");
    }

    @Test
    void detectsOpinewReviewsScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.durexindia.com/products/durex-delay-spray-for-men-20g",
                """
                        <script src="//www.durexindia.com/cdn/shop/t/602/assets/opinew-reviews-product-page.js"></script>
                        <script src="https://cdn.opinew.com/js/opinew-active.js?shop=durex-in.myshopify.com"></script>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.OPINEW);
        assertThat(result.providerKey()).isEqualTo("durex-in.myshopify.com");
        assertThat(result.evidence()).contains("cdn.opinew.com");
    }

    @Test
    void detectsAirReviewsScript() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://www.terrebleu.ca/products/lavender-lip-balm",
                """
                        <script src="https://cdn.shopify.com/extensions/air-reviews-1-101/assets/air-reviews.js"></script>
                        <div class="AirReviews-Widget AirReviews-Widget--Stars" data-review-count="6"></div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.AIR_REVIEWS);
        assertThat(result.evidence()).contains("air-reviews");
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
    void doesNotClassifyYotpoLoyaltyLoaderAsYotpoReviewsWithoutReviewWidget() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://merchant.example",
                """
                        <script src="https://cdn-widgetsrepository.yotpo.com/v1/loader/AEAy30xTnFsiEhLNLnsh0Q"></script>
                        <div>No product review widget here.</div>
                        """
        );

        assertThat(result.provider()).isEqualTo(ReviewProviderType.NONE);
    }

    @Test
    void doesNotClassifyPageflyIntegrationListAsOpinewReviews() {
        ReviewProviderDetectionResult result = detectionService.detect(
                "https://merchant.example",
                """
                        <script>
                          window.__pageflyIntegrations = {
                            "Opinew": "https://cdn.shopify.com/extensions/pagefly/assets/pagefly-3rd-elements.js"
                          };
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
