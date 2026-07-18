package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultProvenance;

/** Exact, current, server-owned offer identity admitted for a cart mutation. */
public record ResolvedSelectedOffer(
        String canonicalProductKey,
        String offerKey,
        OfferIdentity identity,
        ResultProvenance provenance,
        CatalogProductReference rehydratedReference,
        RehydratedCommercialFacts commercialFacts
) {
    public ResolvedSelectedOffer(
            String canonicalProductKey,
            String offerKey,
            OfferIdentity identity,
            ResultProvenance provenance,
            CatalogProductReference rehydratedReference
    ) {
        this(canonicalProductKey, offerKey, identity, provenance, rehydratedReference, null);
    }
}
