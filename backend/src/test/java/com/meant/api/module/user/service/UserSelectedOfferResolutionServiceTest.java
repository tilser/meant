package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyMetrics;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.ResolveUserSelectedOfferQuery;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import com.meant.api.provider.shopify.catalog.ShopifyOfferIdentityStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserSelectedOfferResolutionServiceTest {
    private final StubRehydrationProvider provider = new StubRehydrationProvider();
    private final CatalogProductRehydrationService rehydrationService = new CatalogProductRehydrationService(
            List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
    private final UserCanonicalProductSessionStore sessionStore =
            new UserCanonicalProductSessionStore(Duration.ofMinutes(5), 10);
    private final StubSavedProductPersistenceService savedProductPersistenceService =
            new StubSavedProductPersistenceService();
    private final StubCatalogDataUsePolicy savedProductPolicy = new StubCatalogDataUsePolicy();
    private final CatalogDataUsePolicyResolver policyResolver = new CatalogDataUsePolicyResolver(
            List.of(savedProductPolicy),
            new CatalogDataUsePolicyMetrics(new SimpleMeterRegistry())
    );
    private final UserSavedProductOfferResolutionService savedProductOfferResolutionService =
            new UserSavedProductOfferResolutionService(
                    savedProductPersistenceService,
                    new UserSavedProductResultMapper(
                            new ObjectMapper(),
                            new CatalogPurchaseReferencePolicyResolver(List.of())),
                    policyResolver,
                    List.of(new ShopifyOfferIdentityStrategy())
            );
    private final UserSelectedOfferResolutionService service = new UserSelectedOfferResolutionService(
            sessionStore,
            rehydrationService,
            savedProductOfferResolutionService,
            new SelectedOfferResolutionMetrics(new SimpleMeterRegistry())
    );

    private UUID userId;
    private Offer offer;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        offer = offer();
        sessionStore.remember(
                userId,
                List.of(new CanonicalProduct(
                        "product-key", "Product", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), offer.provenance(), List.of(offer))),
                Map.of(), Map.of(), List.of());
    }

    @Test
    void resolvesOnlyTheAuthenticatedUsersExactCurrentOffer() {
        provider.mode = Mode.FRESH;

        var resolved = service.resolve(new ResolveUserSelectedOfferQuery(userId, offer.key()));

        assertThat(resolved.offerKey()).isEqualTo(offer.key());
        assertThat(resolved.identity()).isEqualTo(offer.identity());
        assertThat(resolved.provenance()).isEqualTo(offer.provenance().getFirst());
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void resolvesMultipleOffersThroughOneBatchedRehydrationCall() {
        Offer second = offer("product-2", "variant-2");
        sessionStore.remember(
                userId,
                List.of(new CanonicalProduct(
                        "product-key", "Product", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), java.util.stream.Stream.concat(
                                offer.provenance().stream(), second.provenance().stream()).toList(),
                        List.of(offer, second))),
                Map.of(), Map.of(), List.of());

        var resolved = service.resolveAll(new ResolveUserSelectedOffersQuery(
                userId, List.of(offer.key(), second.key()), "GB"));

        assertThat(resolved).extracting(value -> value.offerKey())
                .containsExactly(offer.key(), second.key());
        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.lastBatchSize).isEqualTo(2);
    }

    @Test
    void doesNotRevealWhetherAnotherUserOwnsAnOfferKey() {
        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(UUID.randomUUID(), offer.key())))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.WRONG_USER);
        assertThat(provider.calls).isZero();
    }

    @Test
    void rejectsAnUnknownOfferWithoutCallingAProvider() {
        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, "unknown-offer")))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.UNKNOWN_OR_EXPIRED);
        assertThat(provider.calls).isZero();
    }

    @Test
    void rejectsAnExpiredOfferWithoutCallingAProvider() {
        UserCanonicalProductSessionStore expiredStore = new UserCanonicalProductSessionStore(Duration.ZERO, 10);
        expiredStore.remember(
                userId,
                List.of(new CanonicalProduct(
                        "product-key", "Product", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), offer.provenance(), List.of(offer))),
                Map.of(), Map.of(), List.of());
        UserSelectedOfferResolutionService expiredService = new UserSelectedOfferResolutionService(
                expiredStore,
                rehydrationService,
                savedProductOfferResolutionService,
                new SelectedOfferResolutionMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> expiredService.resolve(new ResolveUserSelectedOfferQuery(userId, offer.key())))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.UNKNOWN_OR_EXPIRED);
        assertThat(provider.calls).isZero();
    }

    @Test
    void blocksARehydratedVariantIdentityMismatch() {
        provider.mode = Mode.MISMATCH;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, offer.key())))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH);
    }

    @Test
    void blocksUnknownCommercialAvailabilityBeforeCartMutation() {
        provider.mode = Mode.UNKNOWN;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, offer.key())))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE);
    }

    @Test
    void rejectsThirdEligibleProvenanceWithDifferentRoute() {
        ResultProvenance first = provenance("source-a", "shop-1");
        ResultProvenance second = provenance("source-b", "shop-1");
        ResultProvenance third = provenance("source-c", "shop-2");
        Offer ambiguous = new Offer(
                offer.identity(), "Seller", null, null, null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 2, null), List.of(), null,
                List.of(first, second, third));
        sessionStore.remember(userId, List.of(new CanonicalProduct(
                "product-key", "Product", null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), ambiguous.provenance(), List.of(ambiguous))), Map.of(), Map.of(), List.of());

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, ambiguous.key())))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.AMBIGUOUS_PROVENANCE);
    }

    @Test
    void resolvesSavedOfferFromDurableReferenceWithoutCanonicalSession() {
        UUID savedProductId = UUID.randomUUID();
        String savedOfferKey = savedOffer(savedProductId, null);
        UserSelectedOfferResolutionService noSessionService = new UserSelectedOfferResolutionService(
                new UserCanonicalProductSessionStore(Duration.ZERO, 1),
                rehydrationService,
                savedProductOfferResolutionService,
                new SelectedOfferResolutionMetrics(new SimpleMeterRegistry())
        );

        ResolvedSelectedOffer resolved = noSessionService.resolve(
                new ResolveUserSelectedOfferQuery(userId, savedOfferKey));

        assertThat(resolved.canonicalProductKey()).isEqualTo("saved-product-key");
        assertThat(resolved.offerKey()).isEqualTo(savedOfferKey);
        assertThat(resolved.identity().externalProductIdentity().value())
                .isEqualTo("variant-product:v1:variant-1");
        assertThat(resolved.identity().externalVariantIdentity().value()).isEqualTo("variant-1");
        assertThat(resolved.rehydratedReference().interactionKey()).isEqualTo(savedOfferKey);
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void restoresSavedBundleAndSellingPlanIntoExecutableOfferIdentity() throws Exception {
        OfferComponentIdentity component = new OfferComponentIdentity(
                id(ExternalIdentifierType.PRODUCT, "component-product"),
                id(ExternalIdentifierType.VARIANT, "component-variant"),
                2,
                List.of(new ProductAttribute("variant-option", "Color", "Blue"))
        );
        SellingPlanIdentity sellingPlan = new SellingPlanIdentity(
                id(ExternalIdentifierType.SELLING_PLAN_GROUP, "subscription-group"),
                id(ExternalIdentifierType.SELLING_PLAN, "monthly-plan"),
                List.of(new SellingPlanOption("frequency", "monthly"))
        );
        ObjectMapper objectMapper = new ObjectMapper();
        UUID savedProductId = UUID.randomUUID();
        UserSavedProduct saved = savedProduct(
                savedProductId,
                "seller.example",
                "product-1",
                "variant-1",
                "[]",
                objectMapper.writeValueAsString(List.of(component)),
                objectMapper.writeValueAsString(sellingPlan)
        );
        savedProductPersistenceService.remember(saved);
        savedProductPolicy.decision = CatalogRetentionDecision.identifiersOnly("saved-policy");

        ResolvedSelectedOffer resolved = service.resolve(new ResolveUserSelectedOfferQuery(
                userId, SavedProductOfferKeyCodec.encode(saved)));

        assertThat(resolved.identity().components()).containsExactly(component);
        assertThat(resolved.identity().sellingPlanIdentity()).isEqualTo(sellingPlan);
        assertThat(resolved.rehydratedReference().components()).containsExactly(component);
        assertThat(resolved.rehydratedReference().sellingPlanIdentity()).isEqualTo(sellingPlan);
    }

    @Test
    void batchesSessionAndSavedOfferResolutionInInputOrder() {
        UUID savedProductId = UUID.randomUUID();
        String savedOfferKey = savedOffer(savedProductId, "seller.example");

        List<ResolvedSelectedOffer> resolved = service.resolveAll(new ResolveUserSelectedOffersQuery(
                userId, List.of(offer.key(), savedOfferKey), "US"));

        assertThat(resolved).extracting(ResolvedSelectedOffer::offerKey)
                .containsExactly(offer.key(), savedOfferKey);
        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.lastBatchSize).isEqualTo(2);
    }

    @Test
    void rejectsUnknownOrOtherUsersSavedOfferWithoutCallingProvider() {
        String savedOfferKey = SavedProductOfferKeyCodec.encode(savedProduct(UUID.randomUUID(), null));

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, savedOfferKey)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.UNKNOWN_OR_EXPIRED);
        assertThat(provider.calls).isZero();
    }

    @Test
    void rejectsSavedOfferWhenRetentionPolicyNoLongerMatches() {
        UUID savedProductId = UUID.randomUUID();
        UserSavedProduct saved = savedProduct(savedProductId, null);
        savedProductPersistenceService.remember(saved);
        savedProductPolicy.decision = CatalogRetentionDecision.sessionOnly("revoked-policy");

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(
                userId, SavedProductOfferKeyCodec.encode(saved))))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION);
        assertThat(provider.calls).isZero();
    }

    @Test
    void rejectsDegradedSavedOfferBeforeCartMutation() {
        String savedOfferKey = savedOffer(UUID.randomUUID(), null);
        provider.mode = Mode.DEGRADED;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, savedOfferKey)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE);
    }

    @Test
    void rejectsOutOfStockSavedOfferBeforeCartMutation() {
        String savedOfferKey = savedOffer(UUID.randomUUID(), null);
        provider.mode = Mode.OUT_OF_STOCK;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, savedOfferKey)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE);
    }

    @Test
    void rejectsSavedOfferIdentityMismatch() {
        String savedOfferKey = savedOffer(UUID.randomUUID(), null);
        provider.mode = Mode.MISMATCH;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, savedOfferKey)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH);
    }

    @Test
    void acceptsProviderVerifiedDomainEnrichmentForLegacySavedOffer() {
        String savedOfferKey = savedOffer(UUID.randomUUID(), null);
        provider.mode = Mode.DOMAIN_ENRICHED;

        ResolvedSelectedOffer resolved = service.resolve(
                new ResolveUserSelectedOfferQuery(userId, savedOfferKey));

        assertThat(resolved.rehydratedReference().externalMerchantDomain()).isEqualTo("seller.example");
    }

    @Test
    void rejectsSavedOfferKeyAfterDurableReferenceMutation() {
        UUID savedProductId = UUID.randomUUID();
        UserSavedProduct issued = savedProduct(savedProductId, null);
        savedProductPersistenceService.remember(issued);
        savedProductPolicy.decision = CatalogRetentionDecision.identifiersOnly("saved-policy");
        String issuedKey = SavedProductOfferKeyCodec.encode(issued);
        savedProductPersistenceService.remember(savedProduct(
                savedProductId,
                null,
                "product-mutated",
                "variant-1",
                "[]"
        ));

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(userId, issuedKey)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH);
        assertThat(provider.calls).isZero();
    }

    @Test
    void acceptsShopifyOptionEnrichmentForLegacyEmptySavedOptions() {
        String savedOfferKey = savedOffer(UUID.randomUUID(), null);
        provider.mode = Mode.OPTIONS_ENRICHED;

        ResolvedSelectedOffer resolved = service.resolve(
                new ResolveUserSelectedOfferQuery(userId, savedOfferKey));

        assertThat(resolved.identity().selectedOptions()).containsExactly(largeOption());
        assertThat(resolved.rehydratedReference().selectedOptions()).containsExactly(largeOption());
    }

    @Test
    void rejectsShopifyOptionChangeWhenSavedOptionsWereAlreadyExact() {
        UUID savedProductId = UUID.randomUUID();
        UserSavedProduct saved = savedProduct(
                savedProductId,
                null,
                "product-1",
                "variant-1",
                "[{\"group\":\"variant-option\",\"name\":\"Size\",\"value\":\"Small\"}]"
        );
        savedProductPersistenceService.remember(saved);
        savedProductPolicy.decision = CatalogRetentionDecision.identifiersOnly("saved-policy");
        provider.mode = Mode.OPTIONS_ENRICHED;

        assertThatThrownBy(() -> service.resolve(new ResolveUserSelectedOfferQuery(
                userId, SavedProductOfferKeyCodec.encode(saved))))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH);
    }

    private String savedOffer(UUID savedProductId, String externalMerchantDomain) {
        UserSavedProduct saved = savedProduct(savedProductId, externalMerchantDomain);
        savedProductPersistenceService.remember(saved);
        savedProductPolicy.decision = CatalogRetentionDecision.identifiersOnly("saved-policy");
        return SavedProductOfferKeyCodec.encode(saved);
    }

    private UserSavedProduct savedProduct(UUID savedProductId, String externalMerchantDomain) {
        return savedProduct(
                savedProductId,
                externalMerchantDomain,
                "product-1",
                "variant-1",
                "[]"
        );
    }

    private UserSavedProduct savedProduct(
            UUID savedProductId,
            String externalMerchantDomain,
            String externalProductId,
            String externalVariantId,
            String selectedOptionsJson
    ) {
        return savedProduct(
                savedProductId,
                externalMerchantDomain,
                externalProductId,
                externalVariantId,
                selectedOptionsJson,
                "[]",
                null
        );
    }

    private UserSavedProduct savedProduct(
            UUID savedProductId,
            String externalMerchantDomain,
            String externalProductId,
            String externalVariantId,
            String selectedOptionsJson,
            String componentsJson,
            String sellingPlanJson
    ) {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        return UserSavedProduct.builder()
                .id(savedProductId)
                .userId(userId)
                .productKey("saved-product-key")
                .sourceProvider("SHOPIFY")
                .sourceType(ResultSourceType.PROVIDER_CATALOG.name())
                .sourceIdentity("SHOPIFY_GLOBAL_CATALOG")
                .externalMerchantId("shop-1")
                .externalMerchantDomain(externalMerchantDomain)
                .externalProductId(externalProductId)
                .externalVariantId(externalVariantId)
                .selectedOptionsJson(selectedOptionsJson)
                .componentsJson(componentsJson)
                .sellingPlanJson(sellingPlanJson)
                .retentionPolicyKey("saved-policy")
                .referenceVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private ProductAttribute largeOption() {
        return new ProductAttribute("variant-option", "Size", "Large");
    }

    private DiscoverySourceIdentity savedSource() {
        return new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG"
        );
    }

    private ResultProvenance provenance(String sourceName, String merchantId) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        return new ResultProvenance(
                provider, new DiscoverySourceIdentity(provider, ResultSourceType.PROVIDER_CATALOG, sourceName),
                null, id(ExternalIdentifierType.MERCHANT, merchantId),
                id(ExternalIdentifierType.PRODUCT, "product-1"),
                id(ExternalIdentifierType.VARIANT, "variant-1"), freshness(),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, sourceName, null));
    }

    private Offer offer() {
        return offer("product-1", "variant-1");
    }

    private Offer offer(String productId, String variantId) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        ExternalIdentifier merchant = id(ExternalIdentifierType.MERCHANT, "shop-1");
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(provider, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG"),
                null,
                merchant,
                id(ExternalIdentifierType.PRODUCT, productId),
                id(ExternalIdentifierType.VARIANT, variantId),
                freshness(),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "catalog", null));
        OfferIdentity identity = new OfferIdentity(
                provider, OfferMerchantScope.external(merchant), id(ExternalIdentifierType.PRODUCT, productId),
                id(ExternalIdentifierType.VARIANT, variantId), List.of(), List.of(), null);
        return new Offer(identity, "Seller", null, null, null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 2, null), List.of(), null,
                List.of(provenance));
    }

    private CatalogProductRehydrationResult fresh(CatalogProductReference reference) {
        return CatalogProductRehydrationResult.fresh(reference, reference, facts(reference));
    }

    private RehydratedCommercialFacts facts(CatalogProductReference reference) {
        return new RehydratedCommercialFacts(
                "Product", null, new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 2, null),
                reference.externalVariantReference(), reference.selectedOptions(), List.of(), List.of(), freshness(),
                CommercialFactsFreshness.fromSingleObservation(freshness()));
    }

    private ResultFreshness freshness() {
        return new ResultFreshness(Instant.parse("2026-07-11T10:00:00Z"), Instant.parse("2026-07-11T10:02:00Z"));
    }

    private ExternalIdentifier id(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }

    private enum Mode {
        FRESH,
        MISMATCH,
        UNKNOWN,
        DEGRADED,
        OUT_OF_STOCK,
        DOMAIN_ENRICHED,
        OPTIONS_ENRICHED
    }

    private static final class StubSavedProductPersistenceService extends UserSavedProductPersistenceService {
        private UserSavedProduct saved;

        private StubSavedProductPersistenceService() {
            super(null, null, null, new ObjectMapper());
        }

        void remember(UserSavedProduct saved) {
            this.saved = saved;
        }

        @Override
        public Optional<UserSavedProduct> findVerified(UUID userId, UUID savedProductId) {
            return saved != null
                    && saved.getId().equals(savedProductId)
                    && saved.getUserId().equals(userId)
                    && saved.getReferenceVerifiedAt() != null
                    ? Optional.of(saved)
                    : Optional.empty();
        }
    }

    private final class StubCatalogDataUsePolicy implements CatalogDataUsePolicy {
        private CatalogRetentionDecision decision = CatalogRetentionDecision.identifiersOnly("saved-policy");

        @Override
        public boolean supports(DiscoverySourceIdentity source) {
            return savedSource().equals(source);
        }

        @Override
        public CatalogRetentionDecision decide(
                DiscoverySourceIdentity source,
                CatalogPayloadClass payloadClass
        ) {
            return decision;
        }
    }

    private final class StubRehydrationProvider implements CatalogProductRehydrationProvider {
        private Mode mode = Mode.FRESH;
        private int calls;
        private int lastBatchSize;

        @Override
        public boolean supports(DiscoverySourceIdentity source) {
            return true;
        }

        @Override
        public List<CatalogProductRehydrationResult> rehydrate(
                List<CatalogProductReference> references,
                CatalogRehydrationContext context
        ) {
            calls++;
            lastBatchSize = references.size();
            return references.stream().map(requested -> switch (mode) {
                case FRESH -> fresh(requested);
                case MISMATCH -> CatalogProductRehydrationResult.fresh(
                        requested,
                        new CatalogProductReference(
                                requested.interactionKey(), requested.discoverySource(), null, null,
                                requested.externalMerchantReference(), requested.externalProductReference(),
                                id(ExternalIdentifierType.VARIANT, "variant-tampered"), requested.selectedOptions()),
                        facts(requested));
                case UNKNOWN -> CatalogProductRehydrationResult.fresh(
                        requested,
                        requested,
                        new RehydratedCommercialFacts(
                                "Product", null, OfferAvailability.unknown(), requested.externalVariantReference(),
                                requested.selectedOptions(), List.of(), List.of(), freshness(),
                                CommercialFactsFreshness.fromSingleObservation(freshness())));
                case DEGRADED -> CatalogProductRehydrationResult.failed(
                        requested,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                );
                case OUT_OF_STOCK -> CatalogProductRehydrationResult.fresh(
                        requested,
                        requested,
                        new RehydratedCommercialFacts(
                                "Product", null,
                                new OfferAvailability(OfferAvailabilityStatus.OUT_OF_STOCK, 0, null),
                                requested.externalVariantReference(), requested.selectedOptions(),
                                List.of(), List.of(), freshness(),
                                CommercialFactsFreshness.fromSingleObservation(freshness())));
                case DOMAIN_ENRICHED -> CatalogProductRehydrationResult.fresh(
                        requested,
                        new CatalogProductReference(
                                requested.interactionKey(), requested.discoverySource(),
                                requested.localMerchantId(), requested.localRouting(),
                                requested.externalMerchantReference(), "seller.example",
                                requested.externalProductReference(), requested.externalVariantReference(),
                                requested.selectedOptions()),
                        facts(requested));
                case OPTIONS_ENRICHED -> {
                    CatalogProductReference resolved = new CatalogProductReference(
                            requested.interactionKey(), requested.discoverySource(),
                            requested.localMerchantId(), requested.localRouting(),
                            requested.externalMerchantReference(), requested.externalMerchantDomain(),
                            requested.externalProductReference(), requested.externalVariantReference(),
                            List.of(largeOption()));
                    yield CatalogProductRehydrationResult.fresh(
                            requested,
                            resolved,
                            facts(resolved));
                }
            }).toList();
        }
    }
}
