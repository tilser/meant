package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionReason;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import java.util.Comparator;

record ProductIdentitySignal(
        String key,
        ProductIdentityEvidenceKind kind,
        ProductIdentityEvidence evidence,
        int precedence,
        ProductGroupingDecisionReason reason
) {

    static final Comparator<ProductIdentitySignal> ORDER = Comparator
            .comparingInt(ProductIdentitySignal::precedence)
            .thenComparing(ProductIdentitySignal::key)
            .thenComparingInt(signal -> -signal.evidence().confidenceBasisPoints());

    boolean trustedMergeEvidence() {
        return evidence.strength() == IdentityEvidenceStrength.TRUSTED_EXACT
                && kind != ProductIdentityEvidenceKind.SEMANTIC;
    }
}
