package com.meant.api.module.review.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
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
    private final YotpoReviewClient yotpoReviewClient;
    private final OkendoReviewClient okendoReviewClient;
    private final KlaviyoReviewProperties klaviyoReviewProperties;
    private final YotpoReviewProperties yotpoReviewProperties;
    private final OkendoReviewProperties okendoReviewProperties;
    private final ReviewProductIdNormalizer productIdNormalizer;
    private final Cache<ReviewCacheKey, ProductReviewsResult> cache;

    public ReviewService(
            ReviewProviderRepository reviewProviderRepository,
            KlaviyoReviewClient klaviyoReviewClient,
            YotpoReviewClient yotpoReviewClient,
            OkendoReviewClient okendoReviewClient,
            KlaviyoReviewProperties klaviyoReviewProperties,
            YotpoReviewProperties yotpoReviewProperties,
            OkendoReviewProperties okendoReviewProperties,
            ReviewCacheProperties reviewCacheProperties,
            ReviewProductIdNormalizer productIdNormalizer
    ) {
        this.reviewProviderRepository = reviewProviderRepository;
        this.klaviyoReviewClient = klaviyoReviewClient;
        this.yotpoReviewClient = yotpoReviewClient;
        this.okendoReviewClient = okendoReviewClient;
        this.klaviyoReviewProperties = klaviyoReviewProperties;
        this.yotpoReviewProperties = yotpoReviewProperties;
        this.okendoReviewProperties = okendoReviewProperties;
        this.productIdNormalizer = productIdNormalizer;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(reviewCacheProperties.ttl())
                .maximumSize(reviewCacheProperties.maximumSize())
                .build();
    }

    public ProductReviewsResult getProductReviews(@NotNull @Valid GetProductReviewsQuery query) {
        String productId = productIdNormalizer.normalize(query.productId());
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
        if (!hasFetchableProvider(provider)) {
            return ProductReviewsResult.unsupported(
                    query.merchantId(),
                    productId,
                    provider.getProvider(),
                    "Merchant reviews are not available from a supported provider."
            );
        }

        int limit = query.limit() == null ? defaultLimit(provider) : query.limit();
        ReviewCacheKey cacheKey = new ReviewCacheKey(
                query.merchantId(),
                provider.getProvider(),
                provider.getProviderKey(),
                productId,
                limit,
                offset,
                SORT_MOST_RECENT,
                FILTER,
                MEDIA
        );
        boolean[] loaded = {false};
        ProductReviewsResult result = cache.get(cacheKey, _ -> {
            loaded[0] = true;
            return fetchReviews(query.merchantId(), productId, provider, limit, offset).withCached(false);
        });
        return loaded[0] ? result : result.withCached(true);
    }

    private ProductReviewsResult fetchReviews(
            UUID merchantId,
            String productId,
            ReviewProvider provider,
            int limit,
            int offset
    ) {
        return switch (provider.getProvider()) {
            case KLAVIYO -> klaviyoReviewClient.fetchReviews(
                    merchantId,
                    productId,
                    provider.getProviderKey(),
                    limit,
                    offset
            );
            case YOTPO -> yotpoReviewClient.fetchReviews(
                    merchantId,
                    productId,
                    provider.getProviderKey(),
                    limit,
                    offset
            );
            case OKENDO -> okendoReviewClient.fetchReviews(
                    merchantId,
                    productId,
                    provider.getProviderKey(),
                    limit,
                    offset
            );
            default -> ProductReviewsResult.unsupported(
                    merchantId,
                    productId,
                    provider.getProvider(),
                    "Merchant reviews are not available from a supported provider."
            );
        };
    }

    private boolean hasFetchableProvider(ReviewProvider provider) {
        return (provider.getStatus() == ReviewProviderStatus.DETECTED
                || provider.getStatus() == ReviewProviderStatus.FAILED_RETRYABLE)
                && (provider.getProvider() == ReviewProviderType.KLAVIYO
                || provider.getProvider() == ReviewProviderType.YOTPO
                || provider.getProvider() == ReviewProviderType.OKENDO)
                && hasText(provider.getProviderKey());
    }

    private int defaultLimit(ReviewProvider provider) {
        return switch (provider.getProvider()) {
            case YOTPO -> yotpoReviewProperties.defaultLimit();
            case OKENDO -> okendoReviewProperties.defaultLimit();
            default -> klaviyoReviewProperties.defaultLimit();
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ReviewCacheKey(
            UUID merchantId,
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
