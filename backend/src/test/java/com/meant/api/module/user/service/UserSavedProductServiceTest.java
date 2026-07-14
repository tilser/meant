package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
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
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyMetrics;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class UserSavedProductServiceTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000016");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID INTEGRATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final DiscoverySourceIdentity STOREFRONT_SOURCE = new DiscoverySourceIdentity(
            MerchantCatalogSourceIdentity.PROVIDER,
            ResultSourceType.MERCHANT_STOREFRONT,
            "merchant-verified"
    );

    private FakeRepository repository;
    private FakeRehydrationProvider provider;
    private UserTasteProfileService tasteService;
    private UserSettingsService settingsService;
    private UserSavedProductService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        provider = new FakeRehydrationProvider();
        tasteService = mock(UserTasteProfileService.class);
        settingsService = mock(UserSettingsService.class);
        when(settingsService.get(any())).thenReturn(settings("CZ"));
        service = service(50);
    }

    @Test
    void listBatchesFreshRehydrationOutsideTransactionAndReturnsCurrentFacts() {
        service.save(profile(), product("product-1", "Client title"));
        service.save(profile(), product("product-2", "Other client title"));
        provider.batchSizes.clear();
        provider.contexts.clear();

        List<UserSavedProductResult> results = service.list(profile(), new ListSavedProductsQuery(USER_ID, 0, 10));

        assertThat(provider.batchSizes).containsExactly(2);
        assertThat(provider.transactionActive).containsOnly(false);
        assertThat(provider.contexts).allSatisfy(context -> {
            assertThat(context.country()).isEqualTo("CZ");
            assertThat(context.language()).isNull();
        });
        assertThat(results).allSatisfy(result -> {
            assertThat(result.name()).startsWith("Current ");
            assertThat(result.priceFrom()).isEqualTo(42.0d);
            assertThat(result.priceFromMinorUnits()).isEqualTo(4200L);
            assertThat(result.priceCurrency()).isEqualTo("USD");
            assertThat(result.imageUrl()).startsWith("https://provider.test/");
            assertThat(result.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.price()).isEqualTo(42.0d);
                assertThat(offer.priceMinorUnits()).isEqualTo(4200L);
                assertThat(offer.priceCurrency()).isEqualTo("USD");
                assertThat(offer.delivery()).isNull();
            });
            assertThat(result.marketCountry()).isEqualTo("CZ");
            assertThat(result.marketContextApplied()).isTrue();
            assertThat(result.commercialFactsAuthoritative()).isTrue();
        });
    }

    @Test
    void listRehydratesTheDurableReferenceAfterTheSearchSessionIsGone() {
        service.save(profile(), product("product-1", "Saved title"));
        UserCanonicalProductSessionStore emptySessionStore = mock(UserCanonicalProductSessionStore.class);
        UserSavedProductService restartedService = service(50, emptySessionStore);

        UserSavedProductResult result = restartedService.list(
                profile(), new ListSavedProductsQuery(USER_ID, 0, 10)).getFirst();

        verifyNoInteractions(emptySessionStore);
        assertThat(result.name()).isEqualTo("Current product-1");
        assertThat(result.priceFromMinorUnits()).isEqualTo(4200L);
        assertThat(result.commercialFactsAuthoritative()).isTrue();
    }

    @Test
    void retentionAdmissionPrecedesStablePaginationAndOnlyVisibleRowsRehydrate() {
        service.save(profile(), product("product-1", "Oldest"));
        service.save(profile(), product("product-2", "Middle"));
        service.save(profile(), product("product-stale", "Newest stale"));
        UserSavedProduct stale = repository.products.getLast();
        stale.replaceReference(snapshot(stale, "historical-or-unknown-policy"), Instant.now());
        provider.batchSizes.clear();

        List<UserSavedProductResult> firstPage = service.list(
                profile(), new ListSavedProductsQuery(USER_ID, 0, 1));
        List<UserSavedProductResult> secondPage = service.list(
                profile(), new ListSavedProductsQuery(USER_ID, 1, 1));

        assertThat(firstPage).extracting(UserSavedProductResult::id).containsExactly("product-2");
        assertThat(secondPage).extracting(UserSavedProductResult::id).containsExactly("product-1");
        assertThat(provider.batchSizes).containsExactly(1, 1);
    }

    @Test
    void saveUsesTheServerSessionWithoutCallingTheProviderOrReadingMarketSettings() {
        when(settingsService.get(any())).thenReturn(settings("UK"));
        provider.contexts.clear();

        UserSavedProductResult result = service.save(profile(), product("product-uk", "UK market"));

        assertThat(provider.contexts).isEmpty();
        verify(settingsService, org.mockito.Mockito.never()).get(any());
        assertThat(result.name()).isEqualTo("UK market");
        assertThat(result.imageUrl()).isEqualTo("https://client.test/image.jpg");
        assertThat(result.priceFrom()).isNull();
        assertThat(result.marketCountry()).isNull();
        assertThat(result.marketContextApplied()).isFalse();
        assertThat(result.commercialFactsAuthoritative()).isFalse();
    }

    @Test
    void listNormalizesUkMarketContextToIsoGb() {
        service.save(profile(), product("product-uk", "UK market"));
        provider.contexts.clear();
        when(settingsService.get(any())).thenReturn(settings("UK"));

        UserSavedProductResult result = service.list(
                profile(), new ListSavedProductsQuery(USER_ID, 0, 10)).getFirst();

        assertMarketContext(result, "GB", true);
    }

    @Test
    void listRejectsMalformedAndMissingMarketContext() {
        service.save(profile(), product("product-invalid-context", "Invalid market"));
        for (String countryCode : java.util.Arrays.asList("U1", "GBR", "\u010cZ", "ZZ", null)) {
            provider.contexts.clear();
            when(settingsService.get(any())).thenReturn(settings(countryCode));

            UserSavedProductResult result = service.list(
                    profile(), new ListSavedProductsQuery(USER_ID, 0, 10)).getFirst();

            assertMarketContext(result, null, false);
        }
    }

    @Test
    void degradedListKeepsPresentationButNeverUsesStoredCommercialFacts() {
        service.save(profile(), product("product-1", "Forbidden stored title"));
        provider.available = false;

        UserSavedProductResult result = service.list(
                profile(), new ListSavedProductsQuery(USER_ID, 0, 10)).getFirst();

        assertThat(result.name()).isEqualTo("Forbidden stored title");
        assertThat(result.priceFrom()).isNull();
        assertThat(result.imageUrl()).isEqualTo("https://client.test/image.jpg");
        assertThat(result.review()).isNotNull();
        assertThat(result.offers()).isEmpty();
        assertThat(result.commercialFactsAuthoritative()).isFalse();
    }

    @Test
    void listInvalidatesRowsWhoseStoredPolicyNoLongerMatchesTheReviewedPolicy() {
        service.save(profile(), product("product-1", "Forbidden stored title"));
        UserSavedProduct stored = repository.products.getFirst();
        stored.replaceReference(new UserSavedProduct.DurableReferenceSnapshot(
                stored.getSourceProvider(),
                stored.getSourceType(),
                stored.getSourceIdentity(),
                stored.getLocalMerchantId(),
                stored.getMerchantIntegrationId(),
                stored.getExternalMerchantId(),
                stored.getExternalProductId(),
                stored.getExternalVariantId(),
                stored.getSelectedOptionsJson(),
                "historical-or-unknown-policy"
        ), Instant.now());
        provider.batchSizes.clear();

        assertThat(service.list(profile(), new ListSavedProductsQuery(USER_ID, 0, 10))).isEmpty();
        assertThat(provider.batchSizes).isEmpty();
    }

    @Test
    void savedEntityContainsServerSessionIdentifiersAndPresentationButNoCommercialSnapshot() {
        service.save(profile(), product("product-1", "Forbidden stored title"));

        UserSavedProduct stored = repository.products.getFirst();
        assertThat(stored.getSourceProvider()).isEqualTo("GENERIC_UCP");
        assertThat(stored.getSourceIdentity()).isEqualTo("merchant-verified");
        assertThat(stored.getExternalProductId()).isEqualTo("product-1");
        assertThat(stored.getExternalVariantId()).isEqualTo("variant-requested");
        assertThat(stored.getMerchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(stored.getExternalMerchantId()).isEqualTo("merchant-verified");
        assertThat(stored.getReferenceVerifiedAt()).isNotNull();
        assertThat(stored.getName()).isEqualTo("Forbidden stored title");
        assertThat(stored.getImageUrl()).isEqualTo("https://client.test/image.jpg");
        assertThat(UserSavedProduct.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("priceFrom", "offers");
        verify(tasteService).recordSavedProduct(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistenceUsesTheServerSessionReferenceWithoutProviderRehydration() {
        SaveUserProductCommand command = product("product-1", "Client title");

        service.save(profile(), command);

        UserSavedProduct stored = repository.products.getFirst();
        assertThat(command.catalogReference().localRouting().merchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(stored.getMerchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(stored.getExternalMerchantId()).isEqualTo("merchant-verified");
        assertThat(stored.getExternalVariantId()).isEqualTo("variant-requested");
        assertThat(provider.batchSizes).isEmpty();
    }

    @Test
    void saveRejectsAConflictingSelectedOptionGroup() {
        CatalogProductReference conflicting = requestedReference(
                "product-1", new ProductAttribute("selling-plan", "Size", "Large"));

        assertThatThrownBy(() -> service.save(profile(), product("product-1", "Client title", conflicting)))
                .isInstanceOf(UserException.class)
                .hasMessage("Saved product reference does not belong to the current product session");
        assertThat(repository.products).isEmpty();
    }

    @Test
    void quotaAllowsReferenceRefreshButRejectsAnotherInteraction() {
        UserSavedProductService quotaService = service(1);

        quotaService.save(profile(), product("product-1", "One"));
        quotaService.save(profile(), product("product-1", "Updated"));

        assertThat(repository.products).hasSize(1);
        assertThatThrownBy(() -> quotaService.save(profile(), product("product-2", "Two")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("quota exceeded");
    }

    @Test
    void remoteWorkAndShortWriteBoundariesAreExplicit() throws Exception {
        assertThat(UserSavedProductService.class
                .getMethod("save", EnsureUserProfileCommand.class, SaveUserProductCommand.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
        assertThat(UserSavedProductService.class
                .getMethod("list", EnsureUserProfileCommand.class, ListSavedProductsQuery.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)).isFalse();
        assertThat(UserSavedProductPersistenceService.class
                .getMethod("findVerified", UUID.class, int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly()).isTrue();
        assertThat(UserSavedProductPersistenceService.class
                .getMethod("save", SaveUserProductCommand.class, CatalogProductReference.class, String.class, Instant.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }

    private UserSavedProductService service(int quota) {
        return service(quota, sessionStore());
    }

    private UserSavedProductService service(int quota, UserCanonicalProductSessionStore sessionStore) {
        ObjectMapper objectMapper = new ObjectMapper();
        UserCollectionProperties properties = properties(quota);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        UserSavedProductRepository repositoryProxy = repository.proxy();
        UserSavedProductPersistenceService persistence = new UserSavedProductPersistenceService(
                repositoryProxy, properties, tasteService, objectMapper);
        CatalogDataUsePolicyResolver policies = new CatalogDataUsePolicyResolver(
                List.of(new GenericUcpCatalogDataUsePolicy(
                        new GenericUcpCatalogDataUseProperties(Duration.ofHours(24), Duration.ofMinutes(2)))),
                new CatalogDataUsePolicyMetrics(metrics)
        );
        return new UserSavedProductService(
                mock(UserService.class),
                settingsService,
                repositoryProxy,
                properties,
                new UserSavedProductReferenceResolver(
                        mock(UserProductSearchResultItemRepository.class),
                        sessionStore),
                policies,
                new CatalogProductRehydrationService(
                        List.of(provider),
                        new CatalogProductRehydrationMetrics(metrics)
                ),
                persistence,
                new UserSavedProductResultMapper(objectMapper)
        );
    }

    private UserCanonicalProductSessionStore sessionStore() {
        UserCanonicalProductSessionStore store = mock(UserCanonicalProductSessionStore.class);
        when(store.find(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String productId = invocation.getArgument(1);
                    var product = mock(com.meant.api.module.catalog.service.dto.CanonicalProduct.class);
                    var offer = mock(com.meant.api.module.catalog.service.dto.Offer.class);
                    var provenance = mock(com.meant.api.module.catalog.service.dto.ResultProvenance.class);
                    when(product.key()).thenReturn(productId);
                    when(product.offers()).thenReturn(List.of(offer));
                    when(offer.provenance()).thenReturn(List.of(provenance));
                    when(offer.selectedOptions()).thenReturn(List.of(
                            new ProductAttribute("variant", "Size", "Large")));
                    when(provenance.discoverySource()).thenReturn(STOREFRONT_SOURCE);
                    when(provenance.localRouting()).thenReturn(new LocalMerchantRouting(INTEGRATION_ID));
                    when(provenance.externalMerchantReference()).thenReturn(
                            new ExternalIdentifier(
                                    ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-verified"));
                    when(provenance.externalMerchantDomain()).thenReturn(null);
                    when(provenance.externalProductReference()).thenReturn(
                            new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", productId));
                    when(provenance.externalVariantReference()).thenReturn(
                            new ExternalIdentifier(
                                    ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-requested"));
                    var entry = mock(UserCanonicalProductSessionStore.Entry.class);
                    when(entry.product()).thenReturn(product);
                    return java.util.Optional.of(entry);
                });
        return store;
    }

    private void assertMarketContext(UserSavedProductResult result, String countryCode, boolean applied) {
        assertThat(provider.contexts.getLast().country()).isEqualTo(countryCode);
        assertThat(provider.contexts.getLast().language()).isNull();
        assertThat(result.marketCountry()).isEqualTo(countryCode);
        assertThat(result.marketContextApplied()).isEqualTo(applied);
    }

    private UserSettingsResult settings(String countryCode) {
        UserLocationResult location = countryCode == null
                ? null
                : new UserLocationResult("Czechia", countryCode, "Prague");
        return new UserSettingsResult(
                null,
                null,
                location,
                location == null ? List.of() : List.of(location),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-07-11T00:00:00Z"),
                Instant.parse("2026-07-11T00:00:00Z")
        );
    }

    private UserSavedProduct.DurableReferenceSnapshot snapshot(UserSavedProduct product, String policyKey) {
        return new UserSavedProduct.DurableReferenceSnapshot(
                product.getSourceProvider(),
                product.getSourceType(),
                product.getSourceIdentity(),
                product.getLocalMerchantId(),
                product.getMerchantIntegrationId(),
                product.getExternalMerchantId(),
                product.getExternalProductId(),
                product.getExternalVariantId(),
                product.getSelectedOptionsJson(),
                policyKey
        );
    }

    private SaveUserProductCommand product(String productId, String clientTitle) {
        return product(productId, clientTitle, requestedReference(productId));
    }

    private SaveUserProductCommand product(
            String productId,
            String clientTitle,
            CatalogProductReference reference
    ) {
        return new SaveUserProductCommand(
                USER_ID, productId, "hash", clientTitle, "Client brand", "Client category", "#fff",
                "https://client.test/image.jpg", "https://client.test/product", true, 99, 0.0d, 9,
                List.of("organic"), List.of(), "Generated note", List.of("pro"), List.of("con"),
                new SaveUserProductCommand.Review(5.0d, 500, "Generated review"),
                List.of(new SaveUserProductCommand.Offer(
                        "Client merchant", 0.0d, "Now", "tampered", "tampered.test", "tampered", "Bad", true)),
                null, List.of(), reference
        );
    }

    private CatalogProductReference requestedReference(String productId) {
        return requestedReference(productId, new ProductAttribute(null, "Size", "Large"));
    }

    private CatalogProductReference requestedReference(String productId, ProductAttribute selectedOption) {
        return new CatalogProductReference(
                productId,
                STOREFRONT_SOURCE,
                null,
                new LocalMerchantRouting(INTEGRATION_ID),
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-verified"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", productId),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-requested"),
                List.of(selectedOption)
        );
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(USER_ID, "saved@example.com", "Saved", "User");
    }

    private UserCollectionProperties properties(int quota) {
        int limit = Math.min(50, quota);
        return new UserCollectionProperties(
                new UserCollectionProperties.SavedProducts(limit, 100, quota, limit, limit),
                new UserCollectionProperties.Inventory(50, 100, 500)
        );
    }

    private final class FakeRehydrationProvider implements CatalogProductRehydrationProvider {
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<Boolean> transactionActive = new ArrayList<>();
        private final List<CatalogRehydrationContext> contexts = new ArrayList<>();
        private boolean available = true;

        @Override
        public boolean supports(DiscoverySourceIdentity source) {
            return source != null
                    && MerchantCatalogSourceIdentity.PROVIDER.equals(source.provider())
                    && source.type() == ResultSourceType.MERCHANT_STOREFRONT;
        }

        @Override
        public List<CatalogProductRehydrationResult> rehydrate(
                List<CatalogProductReference> references,
                CatalogRehydrationContext context
        ) {
            batchSizes.add(references.size());
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            contexts.add(context);
            return references.stream().map(this::result).toList();
        }

        private CatalogProductRehydrationResult result(CatalogProductReference requested) {
            if (!available) {
                return CatalogProductRehydrationResult.failed(
                        requested, CatalogRehydrationStatus.DEGRADED, CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
            }
            CatalogProductReference resolved = new CatalogProductReference(
                    requested.interactionKey(),
                    requested.discoverySource(),
                    MERCHANT_ID,
                    new LocalMerchantRouting(INTEGRATION_ID),
                    new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-verified"),
                    requested.externalProductReference(),
                    new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-verified"),
                    List.of()
            );
            ResultFreshness freshness = new ResultFreshness(
                    Instant.parse("2026-07-11T00:00:00Z"), Instant.parse("2026-07-11T00:05:00Z"));
            return CatalogProductRehydrationResult.fresh(requested, resolved, new RehydratedCommercialFacts(
                    "Current " + requested.externalProductReference().value(),
                    new Money(4200, "USD"),
                    new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                    resolved.externalVariantReference(),
                    List.of(),
                    List.of(),
                    List.of(new ProductMedia(
                            ProductMediaType.IMAGE,
                            URI.create("https://provider.test/" + requested.interactionKey() + ".jpg"),
                            null,
                            null,
                            null
                    )),
                    freshness,
                    CommercialFactsFreshness.fromSingleObservation(freshness)
            ));
        }
    }

    private static final class FakeRepository {
        private final List<UserSavedProduct> products = new ArrayList<>();

        UserSavedProductRepository proxy() {
            return (UserSavedProductRepository) Proxy.newProxyInstance(
                    UserSavedProductRepository.class.getClassLoader(),
                    new Class<?>[]{UserSavedProductRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "save" -> save((UserSavedProduct) args[0]);
                        case "findByUserIdAndReferenceVerifiedAtIsNotNullOrderByCreatedAtDescIdDesc" ->
                                page(byUser((UUID) args[0]), (Pageable) args[1]);
                        case "findByUserIdAndProductKey" -> products.stream()
                                .filter(product -> product.getUserId().equals(args[0]))
                                .filter(product -> product.getProductKey().equals(args[1]))
                                .findFirst();
                        case "countByUserIdAndReferenceVerifiedAtIsNotNull" -> (long) byUser((UUID) args[0]).size();
                        case "deleteByUserIdAndProductKey" -> delete((UUID) args[0], (String) args[1]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private UserSavedProduct save(UserSavedProduct product) {
            products.removeIf(existing -> existing.getId().equals(product.getId()));
            products.add(product);
            return product;
        }

        private List<UserSavedProduct> byUser(UUID userId) {
            return products.stream()
                    .filter(product -> product.getUserId().equals(userId))
                    .filter(product -> product.getReferenceVerifiedAt() != null)
                    .sorted(Comparator.comparing(UserSavedProduct::getCreatedAt).reversed()
                            .thenComparing(UserSavedProduct::getId, Comparator.reverseOrder()))
                    .toList();
        }

        private List<UserSavedProduct> page(List<UserSavedProduct> source, Pageable pageable) {
            int start = (int) pageable.getOffset();
            return start >= source.size() ? List.of()
                    : source.subList(start, Math.min(start + pageable.getPageSize(), source.size()));
        }

        private long delete(UUID userId, String productKey) {
            return products.removeIf(product -> product.getUserId().equals(userId)
                    && product.getProductKey().equals(productKey)) ? 1L : 0L;
        }
    }
}
