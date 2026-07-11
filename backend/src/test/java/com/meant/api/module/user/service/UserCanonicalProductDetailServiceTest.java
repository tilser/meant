package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
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
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserCanonicalProductDetailServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-11T08:00:00Z");
    private static final Instant FRESH_AT = Instant.parse("2026-07-11T09:00:00Z");

    private final AtomicInteger providerCalls = new AtomicInteger();
    private final AtomicBoolean substituteIdentity = new AtomicBoolean();
    private final UserCanonicalProductSessionStore store =
            new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
    private CanonicalProduct product;
    private UserCanonicalProductDetailService service;

    @BeforeEach
    void setUp() {
        Offer first = offer("merchant-a", "product-a", "variant-a", 1500);
        Offer second = offer("merchant-b", "product-b", "variant-b", 1400);
        product = new CanonicalProduct(
                "grouped-product-v3_fixture", "Shared product", null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(first.provenance().getFirst(), second.provenance().getFirst()),
                List.of(first, second));
        store.remember(USER_ID, List.of(product), Map.of(), Map.of(), List.of());
        CatalogProductRehydrationProvider provider = new CatalogProductRehydrationProvider() {
            @Override
            public boolean supports(DiscoverySourceIdentity source) {
                return SOURCE.equals(source);
            }

            @Override
            public List<CatalogProductRehydrationResult> rehydrate(
                    List<CatalogProductReference> references,
                    CatalogRehydrationContext context
            ) {
                providerCalls.incrementAndGet();
                ResultFreshness freshness = new ResultFreshness(FRESH_AT, FRESH_AT.plusSeconds(120));
                return references.stream().map(reference -> {
                    if (reference.externalMerchantReference().value().equals("merchant-b")) {
                        return CatalogProductRehydrationResult.failed(
                                reference,
                                CatalogRehydrationStatus.DEGRADED,
                                CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
                    }
                    CatalogProductReference resolved = substituteIdentity.get()
                            ? new CatalogProductReference(
                                    reference.interactionKey(), reference.discoverySource(), reference.localMerchantId(),
                                    reference.localRouting(), reference.externalMerchantReference(),
                                    reference.externalProductReference(),
                                    identifier(ExternalIdentifierType.VARIANT, "substitute-variant"),
                                    reference.selectedOptions())
                            : reference;
                    return CatalogProductRehydrationResult.fresh(reference, resolved, new RehydratedCommercialFacts(
                            "Fresh product",
                            new Money(1300, "USD"),
                            new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 4, null),
                            reference.externalVariantReference(),
                            reference.selectedOptions(),
                            List.of(),
                            List.of(),
                            freshness,
                            CommercialFactsFreshness.fromSingleObservation(freshness)
                    ));
                }).toList();
            }
        };
        service = new UserCanonicalProductDetailService(
                store,
                new CatalogProductRehydrationService(
                        List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry())),
                new StubUserSettingsService());
    }

    @Test
    void defaultsToRecommendedOfferAndBatchRehydratesAllMerchantsOnce() {
        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers()).hasSize(2);
        assertThat(result.recommendedOfferKey()).isEqualTo(product.offers().getFirst().key());
        assertThat(result.selectedOfferKey()).isEqualTo(result.recommendedOfferKey());
        assertThat(result.product().offers().getFirst().price()).isEqualTo(new Money(1300, "USD"));
        assertThat(result.product().offers().get(1).price()).isEqualTo(new Money(1400, "USD"));
        assertThat(result.commercialStates().get(product.offers().getFirst().key()).authority())
                .isEqualTo(UserOfferCommercialState.Authority.REHYDRATED_CURRENT);
        assertThat(result.commercialStates().get(product.offers().get(1).key()).authority())
                .isEqualTo(UserOfferCommercialState.Authority.DISCOVERY_OBSERVATION);
        assertThat(result.commercialStates().get(product.offers().get(1).key()).priceFreshness().freshUntil())
                .isEqualTo(OBSERVED_AT.plusSeconds(60));
        assertThat(result.sourceStates()).anySatisfy(state -> {
            assertThat(state.source()).isEqualTo(SOURCE);
            assertThat(state.rehydrationFailureKind()).isEqualTo(CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
        });
        assertThat(providerCalls).hasValue(1);
    }

    @Test
    void resolvesAnExplicitExactOfferWithoutSubstitutingItsIdentity() {
        String secondKey = product.offers().get(1).key();

        var result = service.get(profile(USER_ID), query(USER_ID, secondKey));

        assertThat(result.selectedOfferKey()).isEqualTo(secondKey);
        assertThat(result.product().offers().get(1).identity()).isEqualTo(product.offers().get(1).identity());
        assertThat(result.product().offers().get(1).price()).isEqualTo(product.offers().get(1).price());
        assertThat(providerCalls).hasValue(1);
    }

    @Test
    void rejectsUnknownAndMismatchedKeysWithoutCallingAProvider() {
        assertThatThrownBy(() -> service.get(profile(USER_ID),
                new GetUserCanonicalProductDetailQuery(USER_ID, "unknown", null)))
                .isInstanceOf(UserException.class);
        assertThatThrownBy(() -> service.get(profile(USER_ID), query(USER_ID, "offer-v2_unknown")))
                .isInstanceOf(UserException.class);

        assertThat(providerCalls).hasValue(0);
    }

    @Test
    void rejectsARehydratedVariantSubstitutionAndRetainsTheExactObservedOffer() {
        substituteIdentity.set(true);

        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers().getFirst().identity()).isEqualTo(product.offers().getFirst().identity());
        assertThat(result.product().offers().getFirst().price()).isEqualTo(product.offers().getFirst().price());
        assertThat(result.commercialStates().get(product.offers().getFirst().key()).degradation())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE);
        assertThat(result.sourceStates()).anySatisfy(state -> assertThat(state.rehydrationFailureKind())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE));
    }

    @Test
    void scopesCanonicalKeysToTheAuthenticatedOwner() {
        assertThatThrownBy(() -> service.get(profile(OTHER_USER_ID),
                new GetUserCanonicalProductDetailQuery(OTHER_USER_ID, product.key(), null)))
                .isInstanceOf(UserException.class);
        assertThat(providerCalls).hasValue(0);
    }

    private GetUserCanonicalProductDetailQuery query(UUID userId, String selectedOfferKey) {
        return new GetUserCanonicalProductDetailQuery(userId, product.key(), selectedOfferKey);
    }

    private EnsureUserProfileCommand profile(UUID userId) {
        return new EnsureUserProfileCommand(userId, "shopper@example.com", "Ada", "Shopper");
    }

    private Offer offer(String merchantValue, String productValue, String variantValue, long price) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, merchantValue);
        ExternalIdentifier productId = identifier(ExternalIdentifierType.PRODUCT, productValue);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, variantValue);
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER, SOURCE, null, merchant, productId, variant,
                new ResultFreshness(OBSERVED_AT, OBSERVED_AT.plusSeconds(60)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, merchantValue, null));
        return new Offer(
                new OfferIdentity(PROVIDER, OfferMerchantScope.external(merchant), productId, variant,
                        List.of(), List.of(), null),
                merchantValue, variantValue, new Money(price, "USD"), null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(), null, List.of(provenance));
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }

    private static final class StubUserSettingsService extends UserSettingsService {
        private StubUserSettingsService() {
            super(null, null, null, null, null);
        }

        @Override
        public UserSettingsResult get(EnsureUserProfileCommand profileCommand) {
            return new UserSettingsResult(
                    null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                    OBSERVED_AT, OBSERVED_AT);
        }
    }
}
