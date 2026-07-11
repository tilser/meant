package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import java.util.Comparator;

/** Authority and freshness of commercial facts returned for one exact offer. */
public record UserOfferCommercialState(
        Authority authority,
        CatalogRehydrationStatus rehydrationStatus,
        CatalogRehydrationFailureKind degradation,
        ResultFreshness priceFreshness,
        ResultFreshness availabilityFreshness,
        ResultFreshness deliveryFreshness
) {
    public static UserOfferCommercialState discovery(Offer offer) {
        ResultFreshness latest = offer.provenance().stream()
                .map(ResultProvenance::freshness)
                .max(Comparator.comparing(ResultFreshness::observedAt))
                .orElseThrow();
        return new UserOfferCommercialState(
                Authority.DISCOVERY_OBSERVATION,
                null,
                null,
                offer.price() == null ? null : latest,
                latest,
                offer.delivery().isEmpty() ? null : latest
        );
    }

    public enum Authority {
        DISCOVERY_OBSERVATION,
        REHYDRATED_CURRENT
    }
}
