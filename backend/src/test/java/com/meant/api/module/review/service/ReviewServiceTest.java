package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import com.meant.api.module.review.properties.KlaviyoReviewProperties;
import com.meant.api.module.review.properties.ReviewCacheProperties;
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
                new KlaviyoReviewProperties("https://reviews.example", 20),
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
                new KlaviyoReviewProperties("https://reviews.example", 20),
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
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        return ReviewProvider.builder()
                .merchantId(merchantId)
                .merchantDomain("merchant.example")
                .provider(ReviewProviderType.KLAVIYO)
                .status(status)
                .providerKey("company-1")
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
}
