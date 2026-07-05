package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import com.meant.api.module.review.properties.KlaviyoReviewProperties;
import com.meant.api.module.review.properties.OkendoReviewProperties;
import com.meant.api.module.review.properties.ReviewCacheProperties;
import com.meant.api.module.review.properties.YotpoReviewProperties;
import com.meant.api.module.review.repository.ReviewProviderRepository;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    @Test
    void returnsCachedResultAfterAtomicCacheLoad() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReviewProviderRepository repository = repositoryReturning(provider(merchantId, ReviewProviderStatus.DETECTED));
        CapturingKlaviyoReviewClient client = new CapturingKlaviyoReviewClient();
        ReviewService service = new ReviewService(
                repository,
                client,
                null,
                null,
                new KlaviyoReviewProperties("https://reviews.example", 20),
                new YotpoReviewProperties("https://yotpo.example", 20),
                new OkendoReviewProperties("https://okendo.example", 20),
                new ReviewCacheProperties(Duration.ofHours(1), 100L),
                new ReviewProductIdNormalizer()
        );
        GetProductReviewsQuery query = new GetProductReviewsQuery(merchantId, "123", null, null);

        ProductReviewsResult first = service.getProductReviews(query);
        ProductReviewsResult second = service.getProductReviews(query);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(client.fetchCount).isEqualTo(1);
    }

    @Test
    void fetchesReviewsFromLastKnownProviderDuringRetryableDiscoveryFailure() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReviewProviderRepository repository = repositoryReturning(
                provider(merchantId, ReviewProviderStatus.FAILED_RETRYABLE)
        );
        CapturingKlaviyoReviewClient client = new CapturingKlaviyoReviewClient();
        ReviewService service = new ReviewService(
                repository,
                client,
                null,
                null,
                new KlaviyoReviewProperties("https://reviews.example", 20),
                new YotpoReviewProperties("https://yotpo.example", 20),
                new OkendoReviewProperties("https://okendo.example", 20),
                new ReviewCacheProperties(Duration.ofHours(1), 100L),
                new ReviewProductIdNormalizer()
        );

        ProductReviewsResult result = service.getProductReviews(
                new GetProductReviewsQuery(merchantId, "123", null, null)
        );

        assertThat(result.supported()).isTrue();
        assertThat(client.fetchCount).isEqualTo(1);
        assertThat(client.providerKey).isEqualTo("company-1");
    }

    @Test
    void fetchesReviewsFromYotpoProvider() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReviewProviderRepository repository = repositoryReturning(
                provider(merchantId, ReviewProviderType.YOTPO, ReviewProviderStatus.DETECTED, "yotpo-store")
        );
        CapturingYotpoReviewClient client = new CapturingYotpoReviewClient();
        ReviewService service = new ReviewService(
                repository,
                null,
                client,
                null,
                new KlaviyoReviewProperties("https://reviews.example", 20),
                new YotpoReviewProperties("https://yotpo.example", 5),
                new OkendoReviewProperties("https://okendo.example", 20),
                new ReviewCacheProperties(Duration.ofHours(1), 100L),
                new ReviewProductIdNormalizer()
        );

        ProductReviewsResult result = service.getProductReviews(
                new GetProductReviewsQuery(merchantId, "gid://shopify/Product/7365959123057", null, null)
        );

        assertThat(result.supported()).isTrue();
        assertThat(result.provider()).isEqualTo(ReviewProviderType.YOTPO);
        assertThat(client.fetchCount).isEqualTo(1);
        assertThat(client.productId).isEqualTo("7365959123057");
        assertThat(client.providerKey).isEqualTo("yotpo-store");
        assertThat(client.limit).isEqualTo(5);
        assertThat(client.offset).isZero();
    }

    @Test
    void fetchesReviewsFromOkendoProvider() {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReviewProviderRepository repository = repositoryReturning(
                provider(merchantId, ReviewProviderType.OKENDO, ReviewProviderStatus.DETECTED, "okendo-subscriber")
        );
        CapturingOkendoReviewClient client = new CapturingOkendoReviewClient();
        ReviewService service = new ReviewService(
                repository,
                null,
                null,
                client,
                new KlaviyoReviewProperties("https://reviews.example", 20),
                new YotpoReviewProperties("https://yotpo.example", 20),
                new OkendoReviewProperties("https://okendo.example", 8),
                new ReviewCacheProperties(Duration.ofHours(1), 100L),
                new ReviewProductIdNormalizer()
        );

        ProductReviewsResult result = service.getProductReviews(
                new GetProductReviewsQuery(merchantId, "gid://shopify/Product/15265473495425", null, 3)
        );

        assertThat(result.supported()).isTrue();
        assertThat(result.provider()).isEqualTo(ReviewProviderType.OKENDO);
        assertThat(client.fetchCount).isEqualTo(1);
        assertThat(client.productId).isEqualTo("15265473495425");
        assertThat(client.providerKey).isEqualTo("okendo-subscriber");
        assertThat(client.limit).isEqualTo(8);
        assertThat(client.offset).isEqualTo(3);
    }

    private ReviewProviderRepository repositoryReturning(ReviewProvider provider) {
        return (ReviewProviderRepository) Proxy.newProxyInstance(
                ReviewProviderRepository.class.getClassLoader(),
                new Class<?>[]{ReviewProviderRepository.class},
                (_, method, args) -> {
                    if ("findByMerchantId".equals(method.getName())) {
                        return Optional.of(provider);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private ReviewProvider provider(UUID merchantId, ReviewProviderStatus status) {
        return provider(merchantId, ReviewProviderType.KLAVIYO, status, "company-1");
    }

    private ReviewProvider provider(
            UUID merchantId,
            ReviewProviderType providerType,
            ReviewProviderStatus status,
            String providerKey
    ) {
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        return ReviewProvider.builder()
                .merchantId(merchantId)
                .merchantDomain("merchant.example")
                .provider(providerType)
                .status(status)
                .providerKey(providerKey)
                .productIdType(ReviewProductIdType.SHOPIFY_NUMERIC_ID)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static class CapturingKlaviyoReviewClient extends KlaviyoReviewClient {

        private int fetchCount;
        private String providerKey;

        private CapturingKlaviyoReviewClient() {
            super(
                    org.springframework.web.client.RestClient.builder().build(),
                    new KlaviyoReviewProperties("https://reviews.example", 20),
                    null
            );
        }

        @Override
        public ProductReviewsResult fetchReviews(
                UUID merchantId,
                String productId,
                String providerKey,
                int limit,
                int offset
        ) {
            fetchCount++;
            this.providerKey = providerKey;
            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.KLAVIYO,
                    4.5,
                    1,
                    false,
                    List.of(),
                    false,
                    true,
                    null
            );
        }
    }

    private static class CapturingYotpoReviewClient extends YotpoReviewClient {

        private int fetchCount;
        private String productId;
        private String providerKey;
        private int limit;
        private int offset;

        private CapturingYotpoReviewClient() {
            super(
                    org.springframework.web.client.RestClient.builder().build(),
                    new YotpoReviewProperties("https://yotpo.example", 20),
                    null
            );
        }

        @Override
        public ProductReviewsResult fetchReviews(
                UUID merchantId,
                String productId,
                String providerKey,
                int limit,
                int offset
        ) {
            fetchCount++;
            this.productId = productId;
            this.providerKey = providerKey;
            this.limit = limit;
            this.offset = offset;
            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.YOTPO,
                    5.0,
                    5,
                    false,
                    List.of(),
                    false,
                    true,
                    null
            );
        }
    }

    private static class CapturingOkendoReviewClient extends OkendoReviewClient {

        private int fetchCount;
        private String productId;
        private String providerKey;
        private int limit;
        private int offset;

        private CapturingOkendoReviewClient() {
            super(
                    org.springframework.web.client.RestClient.builder().build(),
                    new OkendoReviewProperties("https://okendo.example", 20),
                    null
            );
        }

        @Override
        public ProductReviewsResult fetchReviews(
                UUID merchantId,
                String productId,
                String providerKey,
                int limit,
                int offset
        ) {
            fetchCount++;
            this.productId = productId;
            this.providerKey = providerKey;
            this.limit = limit;
            this.offset = offset;
            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.OKENDO,
                    4.7,
                    116,
                    true,
                    List.of(),
                    false,
                    true,
                    null
            );
        }
    }
}
