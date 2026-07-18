package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
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
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserDiscoverProductResultSetSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserDiscoverProductResultSetQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserDiscoverProductResultSetServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RESULT_SET_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-18T08:00:00Z");
    private static final Instant FRESH_AT = Instant.parse("2026-07-18T09:00:00Z");

    @Test
    void rehydratesHistoricalIdentifiersInOneBatchAndRestoresSessionOfferAnchors() {
        UserDiscoverProductResultSetPersistenceService resultSets =
                mock(UserDiscoverProductResultSetPersistenceService.class);
        UserCanonicalProductReferencePersistenceService references =
                mock(UserCanonicalProductReferencePersistenceService.class);
        UserSettingsService settings = mock(UserSettingsService.class);
        UserCanonicalProductSessionStore sessionStore =
                new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
        CanonicalProduct first = identifierOnlyProduct("canonical-first", "merchant-a", "product-a", "variant-a");
        CanonicalProduct second = identifierOnlyProduct("canonical-second", "merchant-b", "product-b", "variant-b");
        EnsureUserProfileCommand profile = profile();
        GetUserDiscoverProductResultSetQuery query =
                new GetUserDiscoverProductResultSetQuery(USER_ID, CONVERSATION_ID, RESULT_SET_ID);
        when(resultSets.getOwned(query)).thenReturn(new UserDiscoverProductResultSetSnapshot(
                RESULT_SET_ID, 3, List.of(first.key(), "missing-reference", second.key())));
        when(references.findProduct(USER_ID, first.key())).thenReturn(Optional.of(first));
        when(references.findProduct(USER_ID, "missing-reference")).thenReturn(Optional.empty());
        when(references.findProduct(USER_ID, second.key())).thenReturn(Optional.of(second));
        when(settings.get(profile)).thenReturn(emptySettings());

        AtomicInteger providerCalls = new AtomicInteger();
        AtomicBoolean transactionActive = new AtomicBoolean();
        AtomicReference<List<CatalogProductReference>> requestedReferences = new AtomicReference<>();
        CatalogProductRehydrationProvider provider = new CatalogProductRehydrationProvider() {
            @Override
            public boolean supports(DiscoverySourceIdentity source) {
                return SOURCE.equals(source);
            }

            @Override
            public List<CatalogProductRehydrationResult> rehydrate(
                    List<CatalogProductReference> requested,
                    CatalogRehydrationContext context
            ) {
                providerCalls.incrementAndGet();
                transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                requestedReferences.set(List.copyOf(requested));
                ResultFreshness freshness = new ResultFreshness(FRESH_AT, FRESH_AT.plusSeconds(120));
                return requested.stream()
                        .map(reference -> CatalogProductRehydrationResult.fresh(
                                reference,
                                reference,
                                new RehydratedCommercialFacts(
                                        "Current " + reference.externalProductReference().value(),
                                        "Current merchant",
                                        new Money(1299, "USD"),
                                        new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                                        reference.externalVariantReference(),
                                        reference.selectedOptions(),
                                        List.of(),
                                        List.of(),
                                        freshness,
                                        CommercialFactsFreshness.fromSingleObservation(freshness)
                                )))
                        .toList();
            }
        };
        CatalogProductRehydrationService catalogRehydration = new CatalogProductRehydrationService(
                List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
        UserDiscoverProductResultSetService service = new UserDiscoverProductResultSetService(
                resultSets,
                references,
                new UserCanonicalProductRehydrationService(catalogRehydration),
                settings,
                new UserProductPreferenceMatchCuratorService(),
                sessionStore
        );

        var result = service.get(profile, query);

        assertThat(result.resultSetId()).isEqualTo(RESULT_SET_ID);
        assertThat(result.products()).extracting(item -> item.product().key())
                .containsExactly(first.key(), second.key());
        assertThat(result.products()).extracting(item -> item.product().title())
                .containsExactly("Current product-a", "Current product-b");
        assertThat(result.products()).extracting(item -> item.product().offers().getFirst().price())
                .containsOnly(new Money(1299, "USD"));
        assertThat(result.unavailableCount()).isOne();
        assertThat(providerCalls).hasValue(1);
        assertThat(requestedReferences.get()).hasSize(2);
        assertThat(transactionActive).isFalse();
        assertThat(sessionStore.findOffer(USER_ID, first.offers().getFirst().key())).isPresent();
        assertThat(sessionStore.findOffer(USER_ID, second.offers().getFirst().key())).isPresent();
    }

    private CanonicalProduct identifierOnlyProduct(
            String canonicalKey,
            String merchantValue,
            String productValue,
            String variantValue
    ) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, merchantValue);
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, productValue);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, variantValue);
        ResultFreshness freshness = new ResultFreshness(OBSERVED_AT, OBSERVED_AT.plusSeconds(60));
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                merchant,
                product,
                variant,
                freshness,
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, merchantValue, null)
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
                null,
                null,
                null,
                null,
                OfferAvailability.unknown(),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                canonicalKey,
                null,
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

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(USER_ID, "shopper@example.com", "Ada", "Shopper");
    }

    private UserSettingsResult emptySettings() {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                OBSERVED_AT,
                OBSERVED_AT
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
