package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record MerchantCatalogSearchOutcome(
        MerchantCatalogSearchAttemptResult merchantAttempt,
        List<MerchantCatalogProductCandidate> productCandidates
) {
}
