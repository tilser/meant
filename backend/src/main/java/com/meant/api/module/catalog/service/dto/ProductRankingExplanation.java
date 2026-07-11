package com.meant.api.module.catalog.service.dto;

import java.util.List;

/** Redacted product-ranking trace containing only typed feature values and stable versions. */
public record ProductRankingExplanation(
        String rankingVersion,
        String diversityPolicyVersion,
        int scoreBasisPoints,
        int finalRank,
        Execution execution,
        DiversityPolicyOutcome diversityPolicyOutcome,
        DiversityDecision diversityDecision,
        String deterministicTieBreakKey,
        List<Feature> features
) {

    public ProductRankingExplanation {
        if (rankingVersion == null || rankingVersion.isBlank()
                || diversityPolicyVersion == null || diversityPolicyVersion.isBlank()
                || execution == null || diversityPolicyOutcome == null || diversityDecision == null
                || deterministicTieBreakKey == null || deterministicTieBreakKey.isBlank()) {
            throw new IllegalArgumentException("Product ranking explanation metadata is required");
        }
        if (scoreBasisPoints < 0 || scoreBasisPoints > 10_000 || finalRank < 1) {
            throw new IllegalArgumentException("Product ranking score or rank is invalid");
        }
        features = features == null ? List.of() : List.copyOf(features);
    }

    public ProductRankingExplanation withFinalRank(
            int rank,
            DiversityPolicyOutcome policyOutcome,
            DiversityDecision decision
    ) {
        return new ProductRankingExplanation(
                rankingVersion,
                diversityPolicyVersion,
                scoreBasisPoints,
                rank,
                execution,
                policyOutcome,
                decision,
                deterministicTieBreakKey,
                features
        );
    }

    public record Feature(
            Name name,
            Availability availability,
            Integer valueBasisPoints,
            int weight,
            List<String> evidenceVersions
    ) {

        public Feature {
            if (name == null || availability == null || weight < 0) {
                throw new IllegalArgumentException("Product ranking feature metadata is invalid");
            }
            if (availability == Availability.AVAILABLE
                    && (valueBasisPoints == null || valueBasisPoints < 0 || valueBasisPoints > 10_000)) {
                throw new IllegalArgumentException("Available product feature needs a basis-point value");
            }
            if (availability == Availability.UNKNOWN && valueBasisPoints != null) {
                throw new IllegalArgumentException("Unknown product feature must not fabricate a value");
            }
            evidenceVersions = evidenceVersions == null ? List.of() : List.copyOf(evidenceVersions);
        }

        public static Feature available(Name name, int valueBasisPoints, int weight, List<String> versions) {
            return new Feature(name, Availability.AVAILABLE, valueBasisPoints, weight, versions);
        }

        public static Feature unknown(Name name, int weight) {
            return new Feature(name, Availability.UNKNOWN, null, weight, List.of());
        }
    }

    public enum Name {
        CALIBRATED_SOURCE_INTENT_FIT,
        LEXICAL_INTENT_FIT,
        DURABLE_PREFERENCE_FIT,
        INVENTORY_RELATIONSHIP,
        QUALITY_EVIDENCE,
        IDENTITY_CONFIDENCE,
        FRESHNESS,
        MODEL_RERANK
    }

    public enum Availability {
        AVAILABLE,
        UNKNOWN
    }

    public enum Execution {
        DETERMINISTIC,
        MODEL_AUGMENTED,
        MODEL_FALLBACK
    }

    public enum DiversityDecision {
        NONE,
        PROMOTED,
        DEFERRED
    }

    public enum DiversityPolicyOutcome {
        STRICT,
        RELAXED_INFEASIBLE
    }
}
