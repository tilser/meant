package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ResultFreshness;

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
        return discovery(offer, null, null);
    }

    public static UserOfferCommercialState discovery(
            Offer offer,
            CatalogRehydrationStatus rehydrationStatus,
            CatalogRehydrationFailureKind degradation
    ) {
        ResultFreshness attributable = offer.provenance().size() == 1
                ? offer.provenance().getFirst().freshness()
                : null;
        return new UserOfferCommercialState(
                Authority.DISCOVERY_OBSERVATION,
                rehydrationStatus,
                degradation,
                offer.price() == null ? null : attributable,
                offer.availability().status() == OfferAvailabilityStatus.UNKNOWN ? null : attributable,
                offer.delivery().isEmpty() ? null : attributable
        );
    }

    public enum Authority {
        DISCOVERY_OBSERVATION,
        REHYDRATED_CURRENT
    }
}
