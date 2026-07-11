package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
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
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.query.ResolveUserSelectedOfferQuery;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSelectedOfferResolutionServiceTest {
    private final StubRehydrationProvider provider = new StubRehydrationProvider();
    private final CatalogProductRehydrationService rehydrationService = new CatalogProductRehydrationService(
            List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
    private final UserCanonicalProductSessionStore sessionStore =
            new UserCanonicalProductSessionStore(Duration.ofMinutes(5), 10);
    private final UserSelectedOfferResolutionService service = new UserSelectedOfferResolutionService(
            sessionStore,
            rehydrationService,
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
                expiredStore, rehydrationService, new SelectedOfferResolutionMetrics(new SimpleMeterRegistry()));

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

    private enum Mode { FRESH, MISMATCH, UNKNOWN }

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
            }).toList();
        }
    }
}
