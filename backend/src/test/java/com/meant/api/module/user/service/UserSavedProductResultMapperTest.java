package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserSavedProductResultMapperTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private final UserSavedProductResultMapper mapper = new UserSavedProductResultMapper(new ObjectMapper());

    @Test
    void preservesIsoCurrencyExponentAndExactMinorUnitsForFreshOffers() {
        assertMoney("USD", 1234, 12.34d);
        assertMoney("EUR", 1234, 12.34d);
        assertMoney("JPY", 1234, 1234.0d);
        assertMoney("KWD", 1234, 1.234d);
    }

    @Test
    void invalidOrUnrepresentableCurrencyNeverBecomesAuthoritative() {
        for (String currency : List.of("ZZZ", "XXX")) {
            UserSavedProductResult result = result(currency, 1234);

            assertThat(result.priceFrom()).isNull();
            assertThat(result.priceFromMinorUnits()).isNull();
            assertThat(result.priceCurrency()).isNull();
            assertThat(result.offers()).isEmpty();
            assertThat(result.commercialFactsAuthoritative()).isFalse();
        }
    }

    private void assertMoney(String currency, long minorUnits, double majorUnits) {
        UserSavedProductResult result = result(currency, minorUnits);

        assertThat(result.priceFrom()).isEqualTo(majorUnits);
        assertThat(result.priceFromMinorUnits()).isEqualTo(minorUnits);
        assertThat(result.priceCurrency()).isEqualTo(currency);
        assertThat(result.commercialFactsAuthoritative()).isTrue();
        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.price()).isEqualTo(majorUnits);
            assertThat(offer.priceMinorUnits()).isEqualTo(minorUnits);
            assertThat(offer.priceCurrency()).isEqualTo(currency);
            assertThat(offer.delivery()).isNull();
        });
    }

    private UserSavedProductResult result(String currency, long minorUnits) {
        CatalogProductReference reference = reference();
        ResultFreshness freshness = new ResultFreshness(NOW, NOW.plusSeconds(120));
        CatalogProductRehydrationResult rehydrated = CatalogProductRehydrationResult.fresh(
                reference,
                reference,
                new RehydratedCommercialFacts(
                        "Current product",
                        new Money(minorUnits, currency),
                        OfferAvailability.unknown(),
                        reference.externalVariantReference(),
                        List.of(),
                        List.of(),
                        List.of(),
                        freshness,
                        CommercialFactsFreshness.fromSingleObservation(freshness)
                )
        );
        return mapper.result(entity(), rehydrated, new CatalogRehydrationContext("CZ", null));
    }

    private UserSavedProduct entity() {
        return UserSavedProduct.create(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "product-1",
                new UserSavedProduct.DurableReferenceSnapshot(
                        "GENERIC_UCP",
                        "MERCHANT_STOREFRONT",
                        "MEANT_MERCHANT_SEMANTIC",
                        null,
                        null,
                        "merchant-1",
                        "product-1",
                        "variant-1",
                        "[]",
                        "generic-ucp-storefront-v2"
                ),
                NOW
        );
    }

    private CatalogProductReference reference() {
        return new CatalogProductReference(
                "product-1",
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-1"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-1"),
                List.of()
        );
    }
}
