package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.CatalogDataUsePolicyMetrics;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import com.meant.api.module.user.entity.UserCanonicalProductReference;
import com.meant.api.module.user.repository.UserCanonicalProductReferenceRepository;
import com.meant.api.provider.shopify.catalog.ShopifyOfferIdentityStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserCanonicalProductReferencePersistenceServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL"
    );
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T08:00:00Z");

    @Test
    void persistsOnlyDurableIdentifiersAndRebuildsTheStableOfferAfterRestart() {
        UserCanonicalProductReferenceRepository repository = mock(UserCanonicalProductReferenceRepository.class);
        MutablePolicy policy = new MutablePolicy(CatalogRetentionDecision.identifiersOnly("policy-v1"));
        UserCanonicalProductReferencePersistenceService service = service(repository, policy);
        AtomicReference<List<UserCanonicalProductReference>> saved = captureSaved(repository);
        CanonicalProduct product = product();

        service.replace(USER_ID, List.of(product));

        assertThat(saved.get()).singleElement().satisfies(reference -> {
            assertThat(reference.getUserId()).isEqualTo(USER_ID);
            assertThat(reference.getCanonicalProductKey()).isEqualTo(product.key());
            assertThat(reference.getOfferKey()).isEqualTo(product.offers().getFirst().key());
            assertThat(reference.getSourceProvider()).isEqualTo("SHOPIFY");
            assertThat(reference.getSourceIdentity()).isEqualTo("SHOPIFY_GLOBAL");
            assertThat(reference.getExternalMerchantId()).isEqualTo("merchant-1");
            assertThat(reference.getExternalMerchantDomain()).isEqualTo("shop.example");
            assertThat(reference.getExternalProductId()).isEqualTo("product-1");
            assertThat(reference.getExternalVariantId()).isEqualTo("variant-1");
            assertThat(reference.getSelectedOptionsJson()).contains("Color", "Blue");
            assertThat(reference.getRetentionPolicyKey()).isEqualTo("policy-v1");
        });
        verify(repository).deleteByUserIdAndCanonicalProductKey(USER_ID, product.key());

        when(repository.findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
                USER_ID, product.key())).thenReturn(saved.get());
        when(repository.findByUserIdAndOfferKeyOrderByReferenceVerifiedAtDescIdAsc(
                USER_ID, product.offers().getFirst().key())).thenReturn(saved.get());

        CanonicalProduct restored = service.findProduct(USER_ID, product.key()).orElseThrow();
        CanonicalProduct restoredByOffer =
                service.findProductByOffer(USER_ID, product.offers().getFirst().key()).orElseThrow();

        assertThat(restored.title()).isNull();
        assertThat(restored.description()).isNull();
        assertThat(restored.media()).isEmpty();
        assertThat(restored.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.key()).isEqualTo(product.offers().getFirst().key());
            assertThat(offer.merchantName()).isEqualTo("shop.example");
            assertThat(offer.price()).isNull();
            assertThat(offer.selectedOptions())
                    .containsExactly(new ProductAttribute("variant-option", "Color", "Blue"));
        });
        assertThat(restoredByOffer.key()).isEqualTo(product.key());
        assertThat(restoredByOffer.offers()).extracting(Offer::key)
                .containsExactly(product.offers().getFirst().key());
    }

    @Test
    void rejectsStoredReferencesWhenTheCurrentPolicyModeOrKeyChanges() {
        UserCanonicalProductReferenceRepository repository = mock(UserCanonicalProductReferenceRepository.class);
        MutablePolicy policy = new MutablePolicy(CatalogRetentionDecision.identifiersOnly("policy-v1"));
        UserCanonicalProductReferencePersistenceService service = service(repository, policy);
        AtomicReference<List<UserCanonicalProductReference>> saved = captureSaved(repository);
        CanonicalProduct product = product();
        service.replace(USER_ID, List.of(product));
        when(repository.findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
                USER_ID, product.key())).thenReturn(saved.get());

        policy.decision = CatalogRetentionDecision.sessionOnly("policy-v1");
        assertThat(service.findProduct(USER_ID, product.key())).isEmpty();

        policy.decision = CatalogRetentionDecision.identifiersOnly("policy-v2");
        assertThat(service.findProduct(USER_ID, product.key())).isEmpty();
    }

    @Test
    void batchRestoresPolicyApprovedReferencesInRequestedKeyOrder() {
        UserCanonicalProductReferenceRepository repository = mock(UserCanonicalProductReferenceRepository.class);
        MutablePolicy policy = new MutablePolicy(CatalogRetentionDecision.identifiersOnly("policy-v1"));
        UserCanonicalProductReferencePersistenceService service = service(repository, policy);
        AtomicReference<List<UserCanonicalProductReference>> saved = captureSaved(repository);
        CanonicalProduct first = product("grouped-product-v3_first", "1");
        CanonicalProduct second = product("grouped-product-v3_second", "2");
        service.replace(USER_ID, List.of(first, second));
        List<UserCanonicalProductReference> reversed = List.of(saved.get().get(1), saved.get().get(0));
        when(repository
                .findByUserIdAndCanonicalProductKeyInOrderByCanonicalProductKeyAscOfferRankAscIdAsc(
                        USER_ID, List.of(first.key(), second.key())))
                .thenReturn(reversed);

        Map<String, CanonicalProduct> restored = service.findProducts(
                USER_ID, List.of(first.key(), second.key(), first.key()));

        assertThat(restored.keySet()).containsExactly(first.key(), second.key());
        assertThat(restored.get(first.key()).offers()).singleElement().satisfies(offer ->
                assertThat(offer.identity().externalVariantIdentity().value()).isEqualTo("variant-1"));
        assertThat(restored.get(second.key()).offers()).singleElement().satisfies(offer ->
                assertThat(offer.identity().externalVariantIdentity().value()).isEqualTo("variant-2"));

        policy.decision = CatalogRetentionDecision.identifiersOnly("policy-v2");
        assertThat(service.findProducts(USER_ID, List.of(first.key(), second.key()))).isEmpty();
    }

    private AtomicReference<List<UserCanonicalProductReference>> captureSaved(
            UserCanonicalProductReferenceRepository repository
    ) {
        AtomicReference<List<UserCanonicalProductReference>> saved = new AtomicReference<>(List.of());
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<UserCanonicalProductReference> values = invocation.getArgument(0);
            List<UserCanonicalProductReference> captured = new ArrayList<>();
            values.forEach(captured::add);
            List<UserCanonicalProductReference> accumulated = new ArrayList<>(saved.get());
            accumulated.addAll(captured);
            saved.set(List.copyOf(accumulated));
            return captured;
        });
        return saved;
    }

    private UserCanonicalProductReferencePersistenceService service(
            UserCanonicalProductReferenceRepository repository,
            MutablePolicy policy
    ) {
        return new UserCanonicalProductReferencePersistenceService(
                repository,
                new CatalogDataUsePolicyResolver(
                        List.of(policy),
                        new CatalogDataUsePolicyMetrics(new SimpleMeterRegistry())
                ),
                new ObjectMapper(),
                List.of(new ShopifyOfferIdentityStrategy())
        );
    }

    private CanonicalProduct product() {
        return product("grouped-product-v3_history", "1");
    }

    private CanonicalProduct product(String canonicalProductKey, String suffix) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-" + suffix);
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-" + suffix);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-" + suffix);
        ProductAttribute color = new ProductAttribute("variant-option", "Color", "Blue");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                merchant,
                "shop.example",
                product,
                variant,
                new ResultFreshness(OBSERVED_AT, null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "fixture", null)
        );
        OfferIdentity identity = new OfferIdentity(
                PROVIDER,
                OfferMerchantScope.external(merchant),
                new ShopifyOfferIdentityStrategy().product(PROVIDER, product, variant),
                variant,
                List.of(color),
                List.of(),
                null
        );
        Offer offer = new Offer(
                identity,
                "Fixture shop",
                "Blue",
                new Money(4_900, "USD"),
                new Money(8_900, "USD"),
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 3, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                canonicalProductKey,
                "Persisted display title",
                "Persisted display description",
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

    private static final class MutablePolicy implements CatalogDataUsePolicy {
        private CatalogRetentionDecision decision;

        private MutablePolicy(CatalogRetentionDecision decision) {
            this.decision = decision;
        }

        @Override
        public boolean supports(DiscoverySourceIdentity source) {
            return SOURCE.equals(source);
        }

        @Override
        public CatalogRetentionDecision decide(
                DiscoverySourceIdentity source,
                CatalogPayloadClass payloadClass
        ) {
            return decision;
        }
    }
}
