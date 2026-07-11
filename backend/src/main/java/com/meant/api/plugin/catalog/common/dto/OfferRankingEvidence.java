package com.meant.api.plugin.catalog.common.dto;

/** Optional normalized merchant-policy evidence. Null fields mean the source supplied no fact. */
public record OfferRankingEvidence(
        Integer merchantTrustBasisPoints,
        Integer historicalReliabilityBasisPoints,
        Integer returnPolicyBasisPoints,
        Boolean checkoutCapable
) {

    public static OfferRankingEvidence unknown() {
        return new OfferRankingEvidence(null, null, null, null);
    }

    public OfferRankingEvidence {
        validate(merchantTrustBasisPoints, "Merchant trust");
        validate(historicalReliabilityBasisPoints, "Historical reliability");
        validate(returnPolicyBasisPoints, "Return policy");
    }

    private static void validate(Integer value, String field) {
        if (value != null && (value < 0 || value > 10_000)) {
            throw new IllegalArgumentException(field + " evidence must be between 0 and 10000 basis points");
        }
    }
}
