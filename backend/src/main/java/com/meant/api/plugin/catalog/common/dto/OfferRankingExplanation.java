package com.meant.api.plugin.catalog.common.dto;

import java.util.List;

/** Redacted offer-ranking trace. Unknown commercial facts remain explicitly unknown. */
public record OfferRankingExplanation(
        String rankingVersion,
        int scoreBasisPoints,
        int finalRank,
        CommercialTieBreakPolicy commercialTieBreakPolicy,
        String deterministicTieBreakKey,
        List<Feature> features
) {

    public OfferRankingExplanation {
        if (rankingVersion == null || rankingVersion.isBlank()
                || commercialTieBreakPolicy == null
                || deterministicTieBreakKey == null || deterministicTieBreakKey.isBlank()) {
            throw new IllegalArgumentException("Offer ranking explanation metadata is required");
        }
        if (scoreBasisPoints < 0 || scoreBasisPoints > 10_000 || finalRank < 1) {
            throw new IllegalArgumentException("Offer ranking score or rank is invalid");
        }
        features = features == null ? List.of() : List.copyOf(features);
    }

    public OfferRankingExplanation withFinalRank(int rank) {
        return new OfferRankingExplanation(
                rankingVersion,
                scoreBasisPoints,
                rank,
                commercialTieBreakPolicy,
                deterministicTieBreakKey,
                features
        );
    }

    public record Feature(Name name, Availability availability, Integer valueBasisPoints, int weight) {

        public Feature {
            if (name == null || availability == null || weight < 0) {
                throw new IllegalArgumentException("Offer ranking feature metadata is invalid");
            }
            if (availability == Availability.AVAILABLE
                    && (valueBasisPoints == null || valueBasisPoints < 0 || valueBasisPoints > 10_000)) {
                throw new IllegalArgumentException("Available offer feature needs a basis-point value");
            }
            if (availability == Availability.UNKNOWN && valueBasisPoints != null) {
                throw new IllegalArgumentException("Unknown offer feature must not fabricate a value");
            }
        }

        public static Feature available(Name name, int valueBasisPoints, int weight) {
            return new Feature(name, Availability.AVAILABLE, valueBasisPoints, weight);
        }

        public static Feature unknown(Name name, int weight) {
            return new Feature(name, Availability.UNKNOWN, null, weight);
        }
    }

    public enum Name {
        LANDED_PRICE,
        ITEM_PRICE,
        AVAILABILITY,
        DELIVERY_EVIDENCE,
        MERCHANT_TRUST,
        HISTORICAL_RELIABILITY,
        RETURN_POLICY,
        CHECKOUT_CAPABILITY,
        FRESHNESS,
        DATA_COMPLETENESS
    }

    public enum Availability {
        AVAILABLE,
        UNKNOWN
    }

    public enum CommercialTieBreakPolicy {
        NONE
    }
}
