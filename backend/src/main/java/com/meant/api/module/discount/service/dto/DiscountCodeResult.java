package com.meant.api.module.discount.service.dto;

import java.time.Instant;

public record DiscountCodeResult(
        String code,
        String title,
        String description,
        String sourceUrl,
        Double confidence,
        String restrictions,
        Instant validUntil,
        Instant expiresAt,
        String validationMessage
) {

    public static DiscountCodeResult from(DiscountCodeCandidateEvaluation evaluation) {
        DiscountCodeCandidateSource candidate = evaluation.candidate();
        return new DiscountCodeResult(
                candidate.code(),
                candidate.title(),
                candidate.description(),
                candidate.sourceUrl(),
                candidate.confidence(),
                candidate.restrictions(),
                evaluation.validUntil(),
                evaluation.expiresAt(),
                evaluation.validationMessage()
        );
    }
}
