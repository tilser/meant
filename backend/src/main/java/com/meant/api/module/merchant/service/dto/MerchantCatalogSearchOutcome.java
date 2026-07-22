package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record MerchantCatalogSearchOutcome(
        MerchantCatalogSearchAttemptResult merchantAttempt,
        List<MerchantCatalogProductCandidate> productCandidates,
        boolean hasNextPage
) {

    public MerchantCatalogSearchOutcome(
            MerchantCatalogSearchAttemptResult merchantAttempt,
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
        this(merchantAttempt, productCandidates, false);
    }
}
