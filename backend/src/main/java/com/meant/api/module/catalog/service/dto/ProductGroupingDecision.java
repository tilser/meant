package com.meant.api.module.catalog.service.dto;

import java.util.List;

/** Redacted, typed explanation of one measurable grouping comparison. */
public record ProductGroupingDecision(
        String leftOfferKey,
        String rightOfferKey,
        ProductGroupingDecisionOutcome outcome,
        ProductGroupingDecisionReason reason,
        int confidenceBasisPoints,
        List<ProductIdentityEvidence> evidence,
        List<ProductIdentityContradictionKind> contradictions
) {

    public ProductGroupingDecision {
        if (leftOfferKey == null || leftOfferKey.isBlank()
                || rightOfferKey == null || rightOfferKey.isBlank()
                || outcome == null || reason == null) {
            throw new IllegalArgumentException("Grouping decision identity, outcome, and reason are required");
        }
        if (confidenceBasisPoints < 0 || confidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("Grouping confidence must be between 0 and 10000 basis points");
        }
        leftOfferKey = leftOfferKey.trim();
        rightOfferKey = rightOfferKey.trim();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        contradictions = contradictions == null ? List.of() : contradictions.stream().distinct().sorted().toList();
    }
}
