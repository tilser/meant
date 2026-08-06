package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserCanonicalProductSessionStoreTest {
    private static final String PRODUCT_KEY = "product-v3_shared";
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG");

    @Test
    void copiedAccessMergesTheGuestOfferWithExistingAccountOffers() {
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserCanonicalProductSessionStore store =
                new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
        CanonicalProduct guestProduct = product("guest-merchant", "guest-product", "guest-variant");
        CanonicalProduct targetProduct = product("target-merchant", "target-product", "target-variant");
        store.remember(guestUserId, List.of(guestProduct), Map.of(), Map.of(), List.of());
        store.remember(targetUserId, List.of(targetProduct), Map.of(), Map.of(), List.of());

        int copied = store.copyAccess(guestUserId, targetUserId, List.of(PRODUCT_KEY));

        assertThat(copied).isEqualTo(1);
        assertThat(store.find(targetUserId, PRODUCT_KEY).orElseThrow().product().offers())
                .extracting(Offer::key)
                .containsExactly(
                        guestProduct.offers().getFirst().key(),
                        targetProduct.offers().getFirst().key()
                );
        assertThat(store.findOffer(targetUserId, guestProduct.offers().getFirst().key()))
                .isPresent();
        assertThat(store.findOffer(guestUserId, guestProduct.offers().getFirst().key()))
                .isPresent();
    }

    private CanonicalProduct product(String merchantValue, String productValue, String variantValue) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, merchantValue);
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, productValue);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, variantValue);
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                merchant,
                merchantValue + ".example",
                product,
                variant,
                new ResultFreshness(Instant.parse("2026-08-05T12:00:00Z"), null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "fixture", null)
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                merchantValue,
                variantValue,
                new Money(1_500, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 1, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                PRODUCT_KEY,
                "Shared product",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
