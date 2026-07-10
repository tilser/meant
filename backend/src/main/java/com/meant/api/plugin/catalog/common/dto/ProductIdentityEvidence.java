package com.meant.api.plugin.catalog.common.dto;

import java.util.Comparator;
import java.util.List;

/** Identity assertion with explicit trust, confidence, and source. */
public record ProductIdentityEvidence(
        ProductIdentityEvidenceKind kind,
        IdentityEvidenceStrength strength,
        int confidenceBasisPoints,
        List<ExternalIdentifier> identifiers,
        ResultSourceReference sourceReference
) {

    private static final Comparator<ExternalIdentifier> IDENTIFIER_ORDER = Comparator
            .comparing(ExternalIdentifier::type)
            .thenComparing(identifier -> nullToEmpty(identifier.namespace()))
            .thenComparing(ExternalIdentifier::value);

    public ProductIdentityEvidence {
        if (kind == null || strength == null || sourceReference == null) {
            throw new IllegalArgumentException("Evidence kind, strength, and source must not be null");
        }
        if (confidenceBasisPoints < 0 || confidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("Evidence confidence must be between 0 and 10000 basis points");
        }
        identifiers = identifiers == null
                ? List.of()
                : identifiers.stream().sorted(IDENTIFIER_ORDER).toList();
        if (identifiers.isEmpty()) {
            throw new IllegalArgumentException("Identity evidence must contain at least one identifier");
        }
        if (kind == ProductIdentityEvidenceKind.SEMANTIC && strength != IdentityEvidenceStrength.SEMANTIC) {
            throw new IllegalArgumentException("Semantic evidence cannot be trusted as exact identity evidence");
        }
    }

    public boolean trustedExact() {
        return strength == IdentityEvidenceStrength.TRUSTED_EXACT;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
