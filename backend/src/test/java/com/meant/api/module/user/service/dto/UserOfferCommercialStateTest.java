package com.meant.api.module.user.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.DeliveryMethod;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferDelivery;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserOfferCommercialStateTest {
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("FIXTURE");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "FIXTURE_CATALOG");
    private static final ExternalIdentifier MERCHANT = identifier(ExternalIdentifierType.MERCHANT, "merchant-1");
    private static final ExternalIdentifier PRODUCT = identifier(ExternalIdentifierType.PRODUCT, "product-1");
    private static final ExternalIdentifier VARIANT = identifier(ExternalIdentifierType.VARIANT, "variant-1");

    @Test
    void unknownDiscoveryAvailabilityHasNoFreshnessAndRequiresRefresh() {
        ResultFreshness observed = freshness("2026-07-11T08:00:00Z");
        Offer offer = offer(
                new OfferAvailability(OfferAvailabilityStatus.UNKNOWN, null, null),
                List.of(),
                List.of(provenance("old", observed)));

        UserOfferCommercialState state = UserOfferCommercialState.discovery(offer);

        assertThat(state.priceFreshness()).isEqualTo(observed);
        assertThat(state.availabilityFreshness()).isNull();
        assertThat(state.deliveryFreshness()).isNull();
    }

    @Test
    void mergedObservationsNeverBorrowANewerUnattributedTimestampForCommercialFacts() {
        ResultFreshness older = freshness("2026-07-11T08:00:00Z");
        ResultFreshness newer = freshness("2026-07-11T10:00:00Z");
        Offer merged = offer(
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(new OfferDelivery(DeliveryMethod.SHIPPING, "US", 2, 4, null)),
                List.of(provenance("old", older), provenance("new", newer)));

        UserOfferCommercialState state = UserOfferCommercialState.discovery(merged);

        assertThat(state.priceFreshness()).isNull();
        assertThat(state.availabilityFreshness()).isNull();
        assertThat(state.deliveryFreshness()).isNull();
    }

    private Offer offer(
            OfferAvailability availability,
            List<OfferDelivery> delivery,
            List<ResultProvenance> provenance
    ) {
        return new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(MERCHANT),
                        PRODUCT,
                        VARIANT,
                        List.of(),
                        List.of(),
                        null),
                "Fixture merchant",
                "Fixture variant",
                new Money(1200, "USD"),
                null,
                availability,
                delivery,
                null,
                provenance);
    }

    private ResultProvenance provenance(String reference, ResultFreshness freshness) {
        return new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                MERCHANT,
                PRODUCT,
                VARIANT,
                freshness,
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, reference, null));
    }

    private ResultFreshness freshness(String observedAt) {
        Instant observed = Instant.parse(observedAt);
        return new ResultFreshness(observed, observed.plusSeconds(300));
    }

    private static ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
