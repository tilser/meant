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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, ReviewProvider> providersByMerchantId = reviewProviderRepository
                .findByMerchantIdIn(candidates.stream()
                        .map(ReviewProviderDiscoveryCandidate::merchantId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        ReviewProvider::getMerchantId,
                        provider -> provider,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ReviewProviderDiscoveryCandidate> claimed = new ArrayList<>();
        List<ReviewProvider> newProviders = new ArrayList<>();
        for (ReviewProviderDiscoveryCandidate candidate : candidates) {
            ReviewProvider provider = providersByMerchantId.get(candidate.merchantId());
            if (provider == null) {
                newProviders.add(newProvider(candidate, now, claimExpiresAt));
                claimed.add(candidate);
            } else if (claimExisting(provider, candidate, now, claimExpiresAt)) {
                claimed.add(candidate);
            }
        }
        if (!newProviders.isEmpty()) {
            reviewProviderRepository.saveAll(newProviders);
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

    private ReviewProvider newProvider(
            ReviewProviderDiscoveryCandidate candidate,
            Instant now,
            Instant claimExpiresAt
    ) {
        return ReviewProvider.builder()
                .merchantId(candidate.merchantId())
                .merchantDomain(candidate.merchantDomain())
                .provider(ReviewProviderType.UNKNOWN)
                .status(ReviewProviderStatus.FAILED_RETRYABLE)
                .productIdType(ReviewProductIdType.UNKNOWN)
                .nextCheckAt(claimExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private boolean claimable(ReviewProvider provider, Instant now) {
        return RETRYABLE_STATUSES.contains(provider.getStatus())
                && provider.getNextCheckAt() != null
                && !provider.getNextCheckAt().isAfter(now);
    }
}
