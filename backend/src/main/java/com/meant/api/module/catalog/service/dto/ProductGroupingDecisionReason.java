package com.meant.api.module.catalog.service.dto;

/** Stable explanation codes for exact product reconciliation decisions. */
public enum ProductGroupingDecisionReason {
    EXACT_OFFER,
    SAME_MERCHANT_PRODUCT,
    TRUSTED_PROVIDER_GROUP,
    UNIVERSAL_IDENTIFIER,
    VERIFIED_BRAND_MODEL,
    VERIFIED_PROVIDER_MAPPING,
    SAME_MERCHANT_CANONICAL_URL,
    CONTRADICTION_VETO,
    TRANSITIVE_CONTRADICTION_VETO,
    LOW_CONFIDENCE_EVIDENCE,
    SEMANTIC_EVIDENCE_ONLY
}
