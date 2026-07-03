package com.meant.api.module.review.service;

import com.meant.api.module.review.entity.ReviewProvider;
import com.meant.api.module.review.repository.ReviewProviderRepository;
import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewProviderPersistenceService {

    private static final int ERROR_MESSAGE_LIMIT = 500;

    private final ReviewProviderRepository reviewProviderRepository;

    @Transactional
    public void persistDetected(
            ReviewProviderDiscoveryCandidate candidate,
            ReviewProviderDetectionResult detection,
            Instant checkedAt
    ) {
        ReviewProvider provider = provider(candidate, checkedAt);
        provider.markDetected(
                candidate.merchantDomain(),
                detection.provider(),
                detection.providerKey(),
                detection.productIdType(),
                detection.sourceUrl(),
                detection.evidence(),
                checkedAt
        );
        reviewProviderRepository.save(provider);
    }

    @Transactional
    public void persistNotFound(
            ReviewProviderDiscoveryCandidate candidate,
            Instant checkedAt,
            Instant nextCheckAt
    ) {
        ReviewProvider provider = provider(candidate, checkedAt);
        provider.markNotFound(candidate.merchantDomain(), checkedAt, nextCheckAt);
        reviewProviderRepository.save(provider);
    }

    @Transactional
    public void persistRetryableFailure(
            ReviewProviderDiscoveryCandidate candidate,
            Throwable exception,
            Instant checkedAt,
            Instant nextCheckAt
    ) {
        ReviewProvider provider = provider(candidate, checkedAt);
        provider.markRetryableFailure(
                candidate.merchantDomain(),
                errorMessage(exception),
                checkedAt,
                nextCheckAt
        );
        reviewProviderRepository.save(provider);
    }

    private ReviewProvider provider(ReviewProviderDiscoveryCandidate candidate, Instant now) {
        return reviewProviderRepository.findByMerchantId(candidate.merchantId())
                .orElseGet(() -> ReviewProvider.builder()
                        .merchantId(candidate.merchantId())
                        .merchantDomain(candidate.merchantDomain())
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
    }

    private String errorMessage(Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception == null ? "Review provider discovery failed" : exception.getClass().getSimpleName();
        }
        if (message.length() <= ERROR_MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_LIMIT);
    }
}
