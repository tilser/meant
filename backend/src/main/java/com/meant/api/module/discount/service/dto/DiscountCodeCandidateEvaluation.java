package com.meant.api.module.discount.service.dto;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import java.time.Instant;

public record DiscountCodeCandidateEvaluation(
        DiscountCodeCandidateSource candidate,
        DiscountCodeStatus status,
        Instant validUntil,
        Instant validatedAt,
        Instant expiresAt,
        String validationMessage
) {

    public static DiscountCodeCandidateEvaluation fromCached(
            DiscountCodeCandidateSource candidate,
            CachedDiscountCodeCandidate cachedCandidate
    ) {
        return new DiscountCodeCandidateEvaluation(
                candidate,
                cachedCandidate.status(),
                cachedCandidate.validUntil(),
                cachedCandidate.validatedAt(),
                cachedCandidate.expiresAt(),
                cachedCandidate.validationMessage()
        );
    }
}
