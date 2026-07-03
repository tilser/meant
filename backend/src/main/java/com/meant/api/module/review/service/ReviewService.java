package com.meant.api.module.review.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import com.meant.api.module.review.properties.KlaviyoReviewProperties;
import com.meant.api.module.review.properties.ReviewCacheProperties;
import com.meant.api.module.review.repository.ReviewProviderRepository;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ReviewService {

    private static final int DEFAULT_OFFSET = 0;
    private static final int SORT_MOST_RECENT = 3;
    private static final String FILTER = "";
    private static final boolean MEDIA = false;

    private final ReviewProviderRepository reviewProviderRepository;
    private final KlaviyoReviewClient klaviyoReviewClient;
    private final KlaviyoReviewProperties klaviyoReviewProperties;
    private final ReviewProductIdNormalizer productIdNormalizer;
    private final Cache<ReviewCacheKey, ProductReviewsResult> cache;

    public ReviewService(
            ReviewProviderRepository reviewProviderRepository,
            KlaviyoReviewClient klaviyoReviewClient,
            KlaviyoReviewProperties klaviyoReviewProperties,
            ReviewCacheProperties reviewCacheProperties,
            ReviewProductIdNormalizer productIdNormalizer
    ) {
        this.reviewProviderRepository = reviewProviderRepository;
        this.klaviyoReviewClient = klaviyoReviewClient;
        this.klaviyoReviewProperties = klaviyoReviewProperties;
        this.productIdNormalizer = productIdNormalizer;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(reviewCacheProperties.ttl())
                .maximumSize(reviewCacheProperties.maximumSize())
                .build();
    }

    public ProductReviewsResult getProductReviews(@NotNull @Valid GetProductReviewsQuery query) {
        String productId = productIdNormalizer.normalize(query.productId());
        int limit = query.limit() == null ? klaviyoReviewProperties.defaultLimit() : query.limit();
        int offset = query.offset() == null ? DEFAULT_OFFSET : query.offset();
        ReviewProvider provider = reviewProviderRepository.findByMerchantId(query.merchantId()).orElse(null);
        if (provider == null) {
            return ProductReviewsResult.unsupported(
                    query.merchantId(),
                    productId,
                    ReviewProviderType.UNKNOWN,
                    "Review provider has not been discovered for this merchant."
            );
        }
        if (provider.getStatus() != ReviewProviderStatus.DETECTED
                || provider.getProvider() != ReviewProviderType.KLAVIYO
                || !hasText(provider.getProviderKey())) {
            return ProductReviewsResult.unsupported(
                    query.merchantId(),
                    productId,
                    provider.getProvider(),
                    "Merchant reviews are not available from a supported provider."
            );
        }

        ReviewCacheKey cacheKey = new ReviewCacheKey(
                provider.getProvider(),
                provider.getProviderKey(),
                productId,
                limit,
                offset,
                SORT_MOST_RECENT,
                FILTER,
                MEDIA
        );
        ProductReviewsResult cachedResult = cache.getIfPresent(cacheKey);
        if (cachedResult != null) {
            return cachedResult.withCached(true);
        }
        ProductReviewsResult result = klaviyoReviewClient.fetchReviews(
                query.merchantId(),
                productId,
                provider.getProviderKey(),
                limit,
                offset
        ).withCached(false);
        cache.put(cacheKey, result);
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ReviewCacheKey(
            ReviewProviderType provider,
            String providerKey,
            String productId,
            int limit,
            int offset,
            int sort,
            String filter,
            boolean media
    ) {
    }
}
