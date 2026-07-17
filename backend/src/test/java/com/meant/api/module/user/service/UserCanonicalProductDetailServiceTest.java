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
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.user.controller.response.UserCanonicalProductDetailV1Response;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import com.meant.api.provider.shopify.catalog.ShopifyOfferIdentityStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserCanonicalProductDetailServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-11T08:00:00Z");
    private static final Instant FRESH_AT = Instant.parse("2026-07-11T09:00:00Z");
    private static final UUID ROUTING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ROUTING_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID LOCAL_MERCHANT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private final AtomicInteger providerCalls = new AtomicInteger();
    private final AtomicBoolean transactionActiveAtProvider = new AtomicBoolean();
    private final AtomicReference<CatalogRehydrationContext> providerContext = new AtomicReference<>();
    private final AtomicReference<UnaryOperator<CatalogProductReference>> resolvedReferenceMutation =
            new AtomicReference<>(UnaryOperator.identity());
    private final UserCanonicalProductSessionStore store =
            new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
    private final StubUserSettingsService userSettingsService = new StubUserSettingsService();
    private final StubCanonicalProductReferencePersistenceService productReferencePersistenceService =
            new StubCanonicalProductReferencePersistenceService();
    private CanonicalProduct product;
    private CatalogProductRehydrationService productRehydrationService;
    private UserCanonicalProductDetailService service;

    @BeforeEach
    void setUp() {
        Offer first = offer("merchant-a", "product-a", "variant-a", 1500);
        Offer second = offer("merchant-b", "product-b", "variant-b", 1400);
        rememberProduct(first, second);
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
                providerContext.set(context);
                transactionActiveAtProvider.set(TransactionSynchronizationManager.isActualTransactionActive());
                ResultFreshness freshness = new ResultFreshness(FRESH_AT, FRESH_AT.plusSeconds(120));
                return references.stream().map(reference -> {
                    if (reference.externalMerchantReference().value().equals("merchant-b")) {
                        return CatalogProductRehydrationResult.failed(
                                reference,
                                CatalogRehydrationStatus.DEGRADED,
                                CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
                    }
                    CatalogProductReference resolved = resolvedReferenceMutation.get().apply(reference);
                    return CatalogProductRehydrationResult.fresh(reference, resolved, new RehydratedCommercialFacts(
                            "Fresh product",
                            "Current merchant",
                            new Money(1300, "USD"),
                            new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 4, null),
                            reference.externalVariantReference(),
                            reference.selectedOptions(),
                            List.of(),
                            List.of(new ProductMedia(
                                    ProductMediaType.IMAGE,
                                    URI.create("https://current.example/product.jpg"),
                                    "Fresh product",
                                    null,
                                    null
                            )),
                            freshness,
                            CommercialFactsFreshness.fromSingleObservation(freshness)
                    ));
                }).toList();
            }
        };
        productRehydrationService = new CatalogProductRehydrationService(
                List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
        service = new UserCanonicalProductDetailService(
                store,
                productReferencePersistenceService,
                productRehydrationService,
                userSettingsService,
                new UserProductPreferenceMatchCuratorService());
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
    void reopensDurableIdentifiersAfterSessionLossAndRepopulatesVariantAnchors() {
        UserCanonicalProductSessionStore restartedStore =
                new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
        productReferencePersistenceService.product(USER_ID, identifierOnlyProduct(product));
        UserCanonicalProductDetailService restartedService = new UserCanonicalProductDetailService(
                restartedStore,
                productReferencePersistenceService,
                productRehydrationService,
                userSettingsService,
                new UserProductPreferenceMatchCuratorService()
        );

        var result = restartedService.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().title()).isEqualTo("Fresh product");
        assertThat(result.product().media()).singleElement().satisfies(media ->
                assertThat(media.url()).isEqualTo(URI.create("https://current.example/product.jpg")));
        assertThat(result.product().offers()).hasSize(2);
        assertThat(result.product().offers().getFirst().merchantName()).isEqualTo("Current merchant");
        assertThat(result.product().offers().getFirst().price()).isEqualTo(new Money(1300, "USD"));
        assertThat(result.selectedOfferKey()).isEqualTo(product.offers().getFirst().key());
        assertThat(restartedStore.find(USER_ID, product.key())).isPresent();
        assertThat(restartedStore.findOffer(USER_ID, result.selectedOfferKey())).isPresent();
        assertThat(providerCalls).hasValue(1);
    }

    @Test
    void recomputesPersonalizationFromCurrentSettingsForTheRehydratedDetailResponse() {
        UserCanonicalProductPersonalizationResult stalePersonalization =
                new UserCanonicalProductPersonalizationResult(
                        "Product details list gluten-free, matching your saved preference.",
                        List.of("gluten-free"),
                        List.of()
                );
        product = new CanonicalProduct(
                product.key(),
                product.title(),
                "A certified organic product.",
                product.media(),
                product.attributes(),
                product.materials(),
                product.certifications(),
                product.attribution(),
                product.identityEvidence(),
                product.provenance(),
                product.retrievalSignals(),
                product.offers()
        );
        store.remember(
                USER_ID,
                List.of(product),
                Map.of(),
                Map.of(),
                Map.of(product.key(), stalePersonalization),
                List.of()
        );
        userSettingsService.filters(List.of(new ShoppingFilterResult(
                "organic",
                "Organic",
                "Prefer organic products.",
                "shopping",
                "prefer",
                10
        )));

        var result = service.get(profile(USER_ID), query(USER_ID, null));
        UserCanonicalProductDetailV1Response response = UserCanonicalProductDetailV1Response.from(result);

        assertThat(result.personalization()).isNotEqualTo(stalePersonalization);
        assertThat(result.personalization().whyMeantForYou())
                .isEqualTo("Product details list organic, matching your saved preference.");
        assertThat(result.personalization().matchedFilterIds()).containsExactly("organic");
        assertThat(response.product().personalization().whyMeantForYou())
                .isEqualTo(result.personalization().whyMeantForYou());
        assertThat(response.product().personalization().matchedFilterIds()).containsExactly("organic");
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
        resolvedReferenceMutation.set(reference -> copy(
                reference,
                reference.discoverySource(),
                reference.localMerchantId(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalProductReference(),
                identifier(ExternalIdentifierType.VARIANT, "substitute-variant")));

        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers().getFirst().identity()).isEqualTo(product.offers().getFirst().identity());
        assertThat(result.product().offers().getFirst().price()).isEqualTo(product.offers().getFirst().price());
        assertThat(result.commercialStates().get(product.offers().getFirst().key()).degradation())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE);
        assertThat(result.sourceStates()).anySatisfy(state -> assertThat(state.rehydrationFailureKind())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE));
    }

    @Test
    void acceptsFreshShopifyFactsAgainstTheRequestedProvenanceProductReference() {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-a");
        ExternalIdentifier productReference = identifier(
                ExternalIdentifierType.PRODUCT, "gid://shopify/Product/100");
        ExternalIdentifier variant = identifier(
                ExternalIdentifierType.VARIANT, "gid://shopify/ProductVariant/200");
        Offer observed = offer(merchant, productReference, variant, 1500, null);
        ExternalIdentifier variantAnchoredProduct = new ShopifyOfferIdentityStrategy()
                .product(PROVIDER, productReference, variant);
        Offer shopifyOffer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        variantAnchoredProduct,
                        variant,
                        List.of(),
                        List.of(),
                        null),
                observed.merchantName(), observed.variantTitle(), observed.price(), observed.listPrice(),
                observed.availability(), observed.delivery(), observed.checkoutUrl(), observed.rankingEvidence(),
                observed.provenance());
        rememberProduct(shopifyOffer);

        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers().getFirst().identity().externalProductIdentity())
                .isEqualTo(variantAnchoredProduct);
        assertThat(result.product().offers().getFirst().price()).isEqualTo(new Money(1300, "USD"));
        assertThat(result.commercialStates().get(shopifyOffer.key()).authority())
                .isEqualTo(UserOfferCommercialState.Authority.REHYDRATED_CURRENT);
    }

    @Test
    void rejectsARehydratedMerchantSubstitutionWithoutChangingObservedIdentityOrRouting() {
        resolvedReferenceMutation.set(reference -> copy(
                reference,
                reference.discoverySource(),
                reference.localMerchantId(),
                reference.localRouting(),
                identifier(ExternalIdentifierType.MERCHANT, "substitute-merchant"),
                reference.externalProductReference(),
                reference.externalVariantReference()));

        assertInvalidResponseRetainsObservedOffer();
    }

    @Test
    void rejectsARehydratedDiscoverySourceAndProviderSubstitutionWithoutChangingTheObservedOffer() {
        ProviderIdentity otherProvider = new ProviderIdentity("OTHER_PROVIDER");
        DiscoverySourceIdentity otherSource = new DiscoverySourceIdentity(
                otherProvider, ResultSourceType.PROVIDER_CATALOG, "OTHER_CATALOG");
        resolvedReferenceMutation.set(reference -> copy(
                reference,
                otherSource,
                reference.localMerchantId(),
                reference.localRouting(),
                identifier(otherProvider, ExternalIdentifierType.MERCHANT,
                        reference.externalMerchantReference().value()),
                identifier(otherProvider, ExternalIdentifierType.PRODUCT,
                        reference.externalProductReference().value()),
                identifier(otherProvider, ExternalIdentifierType.VARIANT,
                        reference.externalVariantReference().value())));

        assertInvalidResponseRetainsObservedOffer();
    }

    @Test
    void rejectsALocalRoutingSubstitutionAndRetainsTheRequestedIntegrationAnchor() {
        Offer routed = withRouting(product.offers().getFirst(), ROUTING_ID);
        rememberProduct(routed);
        resolvedReferenceMutation.set(reference -> copy(
                reference,
                reference.discoverySource(),
                reference.localMerchantId(),
                new LocalMerchantRouting(OTHER_ROUTING_ID),
                reference.externalMerchantReference(),
                reference.externalProductReference(),
                reference.externalVariantReference()));

        var result = assertInvalidResponseRetainsObservedOffer();

        assertThat(result.product().offers().getFirst().provenance().getFirst().localRouting())
                .isEqualTo(new LocalMerchantRouting(ROUTING_ID));
    }

    @Test
    void permitsVerifiedLocalMerchantEnrichmentWhenRoutingStillAnchorsTheIntegration() {
        Offer routed = withRouting(product.offers().getFirst(), ROUTING_ID);
        rememberProduct(routed);
        resolvedReferenceMutation.set(reference -> copy(
                reference,
                reference.discoverySource(),
                LOCAL_MERCHANT_ID,
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalProductReference(),
                reference.externalVariantReference()));

        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers().getFirst().price()).isEqualTo(new Money(1300, "USD"));
        assertThat(result.commercialStates().get(routed.key()).authority())
                .isEqualTo(UserOfferCommercialState.Authority.REHYDRATED_CURRENT);
    }

    @Test
    void normalizesMarketCountryOncePerRequestAndKeepsRemoteIoOutsideTransactions() {
        userSettingsService.locationCode("UK");

        service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(providerContext.get().country()).isEqualTo("GB");
        assertThat(userSettingsService.calls()).isEqualTo(1);
        assertThat(transactionActiveAtProvider).isFalse();

        userSettingsService.locationCode("ZZ");
        service.get(profile(USER_ID), query(USER_ID, null));
        assertThat(providerContext.get().country()).isNull();
        assertThat(userSettingsService.calls()).isEqualTo(2);

        userSettingsService.locationCode("USA");
        service.get(profile(USER_ID), query(USER_ID, null));
        assertThat(providerContext.get().country()).isNull();
        assertThat(userSettingsService.calls()).isEqualTo(3);
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
        return offer(merchant, productId, variant, price, null);
    }

    private Offer offer(
            ExternalIdentifier merchant,
            ExternalIdentifier productId,
            ExternalIdentifier variant,
            long price,
            LocalMerchantRouting routing
    ) {
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER, SOURCE, routing, merchant, productId, variant,
                new ResultFreshness(OBSERVED_AT, OBSERVED_AT.plusSeconds(60)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, merchant.value(), null));
        return new Offer(
                new OfferIdentity(PROVIDER, OfferMerchantScope.external(merchant), productId, variant,
                        List.of(), List.of(), null),
                merchant.value(), variant.value(), new Money(price, "USD"), null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(), null, List.of(provenance));
    }

    private void rememberProduct(Offer... offers) {
        List<Offer> offerList = List.of(offers);
        product = new CanonicalProduct(
                "grouped-product-v3_fixture", "Shared product", null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                offerList.stream().flatMap(offer -> offer.provenance().stream()).toList(),
                offerList);
        store.remember(USER_ID, List.of(product), Map.of(), Map.of(), List.of());
    }

    private CanonicalProduct identifierOnlyProduct(CanonicalProduct source) {
        List<Offer> offers = source.offers().stream()
                .map(offer -> new Offer(
                        offer.identity(),
                        offer.provenance().getFirst().externalMerchantDomain(),
                        null,
                        null,
                        null,
                        OfferAvailability.unknown(),
                        List.of(),
                        null,
                        offer.rankingEvidence(),
                        offer.provenance()
                ))
                .toList();
        return new CanonicalProduct(
                source.key(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                source.provenance(),
                offers
        );
    }

    private Offer withRouting(Offer source, UUID routingId) {
        ResultProvenance provenance = source.provenance().getFirst();
        ResultProvenance routed = new ResultProvenance(
                provenance.provider(), provenance.discoverySource(), new LocalMerchantRouting(routingId),
                provenance.externalMerchantReference(), provenance.externalProductReference(),
                provenance.externalVariantReference(), provenance.freshness(), provenance.sourceReference());
        return new Offer(
                source.identity(), source.merchantName(), source.variantTitle(), source.price(), source.listPrice(),
                source.availability(), source.delivery(), source.checkoutUrl(), source.rankingEvidence(),
                List.of(routed));
    }

    private CatalogProductReference copy(
            CatalogProductReference source,
            DiscoverySourceIdentity discoverySource,
            UUID localMerchantId,
            LocalMerchantRouting localRouting,
            ExternalIdentifier merchant,
            ExternalIdentifier productReference,
            ExternalIdentifier variant
    ) {
        return new CatalogProductReference(
                source.interactionKey(), discoverySource, localMerchantId, localRouting, merchant,
                productReference, variant, source.selectedOptions());
    }

    private com.meant.api.module.user.service.dto.UserProductDetailResult
            assertInvalidResponseRetainsObservedOffer() {
        Offer observed = product.offers().getFirst();

        var result = service.get(profile(USER_ID), query(USER_ID, null));

        assertThat(result.product().offers().getFirst().identity()).isEqualTo(observed.identity());
        assertThat(result.product().offers().getFirst().price()).isEqualTo(observed.price());
        assertThat(result.product().offers().getFirst().provenance()).isEqualTo(observed.provenance());
        assertThat(result.commercialStates().get(observed.key()).degradation())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE);
        assertThat(result.sourceStates()).anySatisfy(state -> assertThat(state.rehydrationFailureKind())
                .isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE));
        return result;
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return identifier(PROVIDER, type, value);
    }

    private ExternalIdentifier identifier(
            ProviderIdentity provider,
            ExternalIdentifierType type,
            String value
    ) {
        return new ExternalIdentifier(type, provider.value(), value);
    }

    private static final class StubUserSettingsService extends UserSettingsService {
        private final AtomicInteger calls = new AtomicInteger();
        private String locationCode;
        private List<ShoppingFilterResult> filters = List.of();

        private StubUserSettingsService() {
            super(null, null, null, null, null);
        }

        private void locationCode(String value) {
            locationCode = value;
        }

        private void filters(List<ShoppingFilterResult> values) {
            filters = List.copyOf(values);
        }

        private int calls() {
            return calls.get();
        }

        @Override
        public UserSettingsResult get(EnsureUserProfileCommand profileCommand) {
            calls.incrementAndGet();
            UserLocationResult location = locationCode == null
                    ? null
                    : new UserLocationResult("Fixture", locationCode, "Fixture City");
            return new UserSettingsResult(
                    null, null, location, List.of(), filters, List.of(), List.of(), List.of(),
                    OBSERVED_AT, OBSERVED_AT);
        }
    }

    private static final class StubCanonicalProductReferencePersistenceService
            extends UserCanonicalProductReferencePersistenceService {
        private UUID userId;
        private CanonicalProduct product;

        private StubCanonicalProductReferencePersistenceService() {
            super(null, null, null, List.of());
        }

        private void product(UUID owner, CanonicalProduct value) {
            userId = owner;
            product = value;
        }

        @Override
        public Optional<CanonicalProduct> findProduct(UUID owner, String canonicalProductKey) {
            return userId != null
                    && userId.equals(owner)
                    && product != null
                    && product.key().equals(canonicalProductKey)
                    ? Optional.of(product)
                    : Optional.empty();
        }
    }
}
