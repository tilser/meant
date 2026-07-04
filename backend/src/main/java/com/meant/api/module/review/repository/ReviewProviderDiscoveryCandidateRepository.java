package com.meant.api.module.review.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewProviderDiscoveryCandidateRepository {

    private static final List<ReviewProviderStatus> RETRYABLE_STATUSES = List.of(
            ReviewProviderStatus.FAILED_RETRYABLE,
            ReviewProviderStatus.NOT_FOUND
    );
    private static final String JAKARTA_LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";
    private static final int SKIP_LOCKED = -2;

    private final EntityManager entityManager;
    private final ReviewProviderRepository reviewProviderRepository;

    @Transactional
    public List<ReviewProviderDiscoveryCandidate> claimCandidates(Instant now, Instant claimExpiresAt, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<ReviewProviderDiscoveryCandidate> candidates = candidates(now, limit);
        List<ReviewProviderDiscoveryCandidate> claimed = new ArrayList<>();
        for (ReviewProviderDiscoveryCandidate candidate : candidates) {
            if (claim(candidate, now, claimExpiresAt)) {
                claimed.add(candidate);
            }
        }
        return List.copyOf(claimed);
    }

    private List<ReviewProviderDiscoveryCandidate> candidates(Instant now, int limit) {
        return entityManager.createQuery("""
                        select merchant
                        from Merchant merchant
                        left join ReviewProvider provider on provider.merchantId = merchant.id
                        where merchant.active = true
                          and (
                            provider.id is null
                            or (
                              provider.status in :retryableStatuses
                              and provider.nextCheckAt <= :now
                            )
                          )
                        order by coalesce(provider.nextCheckAt, merchant.updatedAt) asc
                        """, Merchant.class)
                .setParameter("retryableStatuses", RETRYABLE_STATUSES)
                .setParameter("now", now)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(JAKARTA_LOCK_TIMEOUT_HINT, SKIP_LOCKED)
                .getResultList()
                .stream()
                .map(merchant -> new ReviewProviderDiscoveryCandidate(merchant.getId(), merchant.getDomain()))
                .toList();
    }

    private boolean claim(
            ReviewProviderDiscoveryCandidate candidate,
            Instant now,
            Instant claimExpiresAt
    ) {
        return reviewProviderRepository.findByMerchantId(candidate.merchantId())
                .map(provider -> claimExisting(provider, candidate, now, claimExpiresAt))
                .orElseGet(() -> claimNew(candidate, now, claimExpiresAt));
    }

    private boolean claimExisting(
            ReviewProvider provider,
            ReviewProviderDiscoveryCandidate candidate,
            Instant now,
            Instant claimExpiresAt
    ) {
        if (!claimable(provider, now)) {
            return false;
        }
        provider.claimDiscoveryLease(candidate.merchantDomain(), claimExpiresAt, now);
        return true;
    }

    private boolean claimNew(ReviewProviderDiscoveryCandidate candidate, Instant now, Instant claimExpiresAt) {
        ReviewProvider provider = ReviewProvider.builder()
                .merchantId(candidate.merchantId())
                .merchantDomain(candidate.merchantDomain())
                .provider(ReviewProviderType.UNKNOWN)
                .status(ReviewProviderStatus.FAILED_RETRYABLE)
                .productIdType(ReviewProductIdType.UNKNOWN)
                .nextCheckAt(claimExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reviewProviderRepository.save(provider);
        return true;
    }

    private boolean claimable(ReviewProvider provider, Instant now) {
        return RETRYABLE_STATUSES.contains(provider.getStatus())
                && provider.getNextCheckAt() != null
                && !provider.getNextCheckAt().isAfter(now);
    }
}
