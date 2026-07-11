package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CommercialFactsFreshness;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.RehydratedCommercialFacts;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyMetrics;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyResolver;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationMetrics;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationProvider;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationService;
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

    private FakeRepository repository;
    private FakeRehydrationProvider provider;
    private UserTasteProfileService tasteService;
    private UserSavedProductService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        provider = new FakeRehydrationProvider();
        tasteService = mock(UserTasteProfileService.class);
        service = service(50);
    }

    @Test
    void listBatchesFreshRehydrationOutsideTransactionAndReturnsCurrentFacts() {
        service.save(profile(), product("product-1", "Client title"));
        service.save(profile(), product("product-2", "Other client title"));
        provider.batchSizes.clear();

        List<UserSavedProductResult> results = service.list(profile(), new ListSavedProductsQuery(USER_ID, 0, 10));

        assertThat(provider.batchSizes).containsExactly(2);
        assertThat(provider.transactionActive).containsOnly(false);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.name()).startsWith("Current ");
            assertThat(result.priceFrom()).isEqualTo(42.0d);
            assertThat(result.imageUrl()).startsWith("https://provider.test/");
            assertThat(result.commercialFactsAuthoritative()).isTrue();
        });
    }

    @Test
    void degradedListReturnsExplicitUnknownFactsNeverZeroOrStoredHints() {
        service.save(profile(), product("product-1", "Forbidden stored title"));
        provider.available = false;

        UserSavedProductResult result = service.list(
                profile(), new ListSavedProductsQuery(USER_ID, 0, 10)).getFirst();

        assertThat(result.name()).isNull();
        assertThat(result.priceFrom()).isNull();
        assertThat(result.imageUrl()).isNull();
        assertThat(result.review()).isNull();
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
    void savedEntityContainsOnlyVerifiedIdentifiersAndNoProviderPayloadFields() {
        service.save(profile(), product("product-1", "Forbidden stored title"));

        UserSavedProduct stored = repository.products.getFirst();
        assertThat(stored.getSourceProvider()).isEqualTo("GENERIC_UCP");
        assertThat(stored.getExternalProductId()).isEqualTo("product-1");
        assertThat(stored.getExternalVariantId()).isEqualTo("variant-verified");
        assertThat(stored.getMerchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(stored.getReferenceVerifiedAt()).isNotNull();
        assertThat(UserSavedProduct.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain(
                        "name", "brand", "category", "tone", "imageUrl", "productUrl", "remote",
                        "matchScore", "priceFrom", "merchantCount", "satisfies", "misses", "note",
                        "pros", "cons", "reviewScore", "reviewCount", "reviewInsight", "offers", "needs", "provides"
                );
        verify(tasteService).recordSavedProduct(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistenceUsesCanonicalReferenceRatherThanClientRoutingTuple() {
        SaveUserProductCommand command = product("product-1", "Client title");

        service.save(profile(), command);

        UserSavedProduct stored = repository.products.getFirst();
        assertThat(command.catalogReference().localRouting()).isNull();
        assertThat(stored.getMerchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(stored.getExternalMerchantId()).isEqualTo("merchant-verified");
        assertThat(stored.getExternalVariantId()).isEqualTo("variant-verified");
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
                .getMethod("findVerified", UUID.class, int.class, int.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class).readOnly()).isTrue();
        assertThat(UserSavedProductPersistenceService.class
                .getMethod("save", SaveUserProductCommand.class, CatalogProductReference.class, String.class, Instant.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNotNull();
    }

    private UserSavedProductService service(int quota) {
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
                repositoryProxy,
                properties,
                new UserSavedProductReferenceResolver(mock(UserProductSearchResultItemRepository.class)),
                policies,
                new CatalogProductRehydrationService(
                        List.of(provider),
                        new CatalogProductRehydrationMetrics(metrics)
                ),
                persistence,
                new UserSavedProductResultMapper(objectMapper)
        );
    }

    private SaveUserProductCommand product(String productId, String clientTitle) {
        return new SaveUserProductCommand(
                USER_ID, productId, "hash", clientTitle, "Client brand", "Client category", "#fff",
                "https://client.test/image.jpg", "https://client.test/product", true, 99, 0.0d, 9,
                List.of("organic"), List.of(), "Generated note", List.of("pro"), List.of("con"),
                new SaveUserProductCommand.Review(5.0d, 500, "Generated review"),
                List.of(new SaveUserProductCommand.Offer(
                        "Client merchant", 0.0d, "Now", "tampered", "tampered.test", "tampered", "Bad", true)),
                null, List.of(), requestedReference(productId)
        );
    }

    private CatalogProductReference requestedReference(String productId) {
        return new CatalogProductReference(
                productId,
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                MERCHANT_ID,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", productId),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-requested"),
                List.of()
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
        private boolean available = true;

        @Override
        public boolean supports(com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity source) {
            return MerchantCatalogSourceIdentity.DISCOVERY_SOURCE.equals(source);
        }

        @Override
        public List<CatalogProductRehydrationResult> rehydrate(
                List<CatalogProductReference> references,
                CatalogRehydrationContext context
        ) {
            batchSizes.add(references.size());
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return references.stream().map(this::result).toList();
        }

        private CatalogProductRehydrationResult result(CatalogProductReference requested) {
            if (!available) {
                return CatalogProductRehydrationResult.failed(
                        requested, CatalogRehydrationStatus.DEGRADED, CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
            }
            CatalogProductReference resolved = new CatalogProductReference(
                    requested.interactionKey(),
                    MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
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
                        case "findByUserIdAndReferenceVerifiedAtIsNotNullOrderByCreatedAtDesc" ->
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
                    .sorted(Comparator.comparing(UserSavedProduct::getCreatedAt).reversed())
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
