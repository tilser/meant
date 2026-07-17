package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.module.catalog.service.dto.IdentityEvidenceStrength;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.dto.ProductRankingContext;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.module.catalog.service.ExactProductGroupingService;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryMetrics;
import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import com.meant.api.module.catalog.service.ProductGroupingMetrics;
import com.meant.api.module.catalog.service.ProductRankingTestFactory;
import com.meant.api.provider.shopify.catalog.ShopifyOfferIdentityStrategy;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class UserGroupedProductSearchServiceTest {

    private static final ScheduledExecutorService DEADLINE_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).factory());

    @Test
    void groupsFederatedCandidatesWithoutUsingTheLegacyCache() {
        UserProductSearchProductResult flatProduct = flatProduct("usd", "usd");
        ProductCandidate candidate = candidateMapper().from(
                flatProduct,
                integration(flatProduct.merchantId()),
                Instant.parse("2026-07-10T10:00:00Z"),
                ResultSourceType.MERCHANT_STOREFRONT
        );
        StubPreparationService preparationService = new StubPreparationService(preparation());
        StubFederatedDiscoveryService discoveryService = new StubFederatedDiscoveryService(
                new FederatedCatalogDiscoveryResult(
                        CatalogDiscoveryTerminalStatus.SUCCESS,
                        List.of(),
                        List.of(candidate),
                        false
                )
        );
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                preparationService,
                discoveryService,
                new ExactProductGroupingService(),
                ProductRankingTestFactory.service(),
                new StubRankingContextFactory(),
                sessionStore(),
                referencePersistence(),
                new UserProductPreferenceMatchCuratorService()
        );
        EnsureUserProfileCommand profile = profile();
        SearchUserProductsCommand command = command(profile.id());

        var result = service.search(profile, command);

        assertThat(result.cached()).isFalse();
        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.identity().provider().value()).isEqualTo("SHOPIFY");
                assertThat(offer.identity().merchantScope().externalMerchantIdentity().value())
                        .isEqualTo("gid://shopify/Shop/100");
                assertThat(offer.identity().externalProductIdentity().value())
                        .isEqualTo("variant-product:v1:gid://shopify/ProductVariant/300");
                assertThat(offer.identity().externalVariantIdentity().type())
                        .isEqualTo(ExternalIdentifierType.VARIANT);
                assertThat(offer.price().minorUnits()).isEqualTo(4200);
            });
        });
        assertThat(discoveryService.request.query()).isEqualTo("linen shirt");
        assertThat(discoveryService.request.candidateLimit()).isEqualTo(100);
        assertThat(preparationService.profileCommand).isEqualTo(profile);
        assertThat(preparationService.searchCommand).isEqualTo(command);
    }

    @Test
    void omitsPriceWhenTheCandidateHasNoCurrency() {
        UserProductSearchProductResult flatProduct = flatProduct(null, null);

        ProductCandidate candidate = candidateMapper().from(
                flatProduct,
                integration(flatProduct.merchantId()),
                Instant.parse("2026-07-10T10:00:00Z"),
                ResultSourceType.MERCHANT_STOREFRONT
        );

        assertThat(candidate.offer().price()).isNull();
        assertThat(candidate.offer().identity().externalProductIdentity().value())
                .isEqualTo("variant-product:v1:gid://shopify/ProductVariant/300");
    }

    @Test
    void successiveNonzeroOffsetsRemainPrefixStableWithoutDuplicatesOrGaps() {
        List<ProductCandidate> generic = pageCandidates("generic", 8);
        List<ProductCandidate> shopify = pageCandidates("shopify", 8);
        UserGroupedProductSearchService service = pagingService(
                new PrefixSource("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", generic, false),
                new PrefixSource("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", shopify, false)
        );
        EnsureUserProfileCommand profile = profile();

        var first = service.search(profile, command(profile.id(), 0, 2));
        var second = service.search(profile, command(profile.id(), 2, 2));
        var third = service.search(profile, command(profile.id(), 4, 2));

        List<String> expectedPrefix = new ExactProductGroupingService().group(
                        java.util.stream.Stream.concat(generic.stream(), shopify.stream()).toList())
                .stream()
                .limit(6)
                .map(product -> product.title())
                .toList();
        assertThat(java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.products().stream())
                .map(product -> product.title())
                .toList())
                .containsExactlyElementsOf(expectedPrefix)
                .doesNotHaveDuplicates();
        assertThat(first.hasMore()).isTrue();
        assertThat(second.hasMore()).isTrue();
        assertThat(third.hasMore()).isTrue();
    }

    @Test
    void sparseAndFailedSourcesStillPageTheTruncatedSuccessfulPrefix() {
        List<ProductCandidate> sparse = pageCandidates("generic", 1);
        List<ProductCandidate> shopify = pageCandidates("shopify", 8);
        EnsureUserProfileCommand profile = profile();
        UserGroupedProductSearchService sparseService = pagingService(
                new PrefixSource("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", sparse, false),
                new PrefixSource("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", shopify, false)
        );
        UserGroupedProductSearchService partialService = pagingService(
                new PrefixSource("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", List.of(), true),
                new PrefixSource("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", shopify, false)
        );

        var sparsePage = sparseService.search(profile, command(profile.id(), 2, 2));
        var partialPage = partialService.search(profile, command(profile.id(), 2, 2));

        List<String> sparseExpected = new ExactProductGroupingService().group(
                        java.util.stream.Stream.concat(sparse.stream(), shopify.stream()).toList())
                .stream().skip(2).limit(2).map(product -> product.title()).toList();
        List<String> partialExpected = new ExactProductGroupingService().group(shopify)
                .stream().skip(2).limit(2).map(product -> product.title()).toList();
        assertThat(sparsePage.products()).extracting(product -> product.title())
                .containsExactlyElementsOf(sparseExpected);
        assertThat(partialPage.products()).extracting(product -> product.title())
                .containsExactlyElementsOf(partialExpected);
        assertThat(sparsePage.hasMore()).isTrue();
        assertThat(partialPage.hasMore()).isTrue();
        assertThat(partialPage.sourceStates()).anySatisfy(state -> {
            assertThat(state.degraded()).isTrue();
            assertThat(state.failureKind()).isEqualTo(CatalogSourceFailureKind.TRANSIENT_UPSTREAM);
        });
    }

    @Test
    void groupsBeforeProductPaginationWithoutSplittingOffersAcrossSuccessivePages() {
        ProductCandidate shared = pageCandidates("shared", 1).getFirst();
        List<ProductCandidate> candidates = new java.util.ArrayList<>();
        candidates.add(pageCandidates("unique-a", 1).getFirst());
        candidates.add(shared);
        candidates.add(shared);
        candidates.addAll(pageCandidates("unique-b", 4));
        UserGroupedProductSearchService service = pagingService(
                new PrefixSource("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", candidates, false)
        );
        EnsureUserProfileCommand profile = profile();

        var first = service.search(profile, command(profile.id(), 0, 2));
        var second = service.search(profile, command(profile.id(), 2, 2));
        var third = service.search(profile, command(profile.id(), 4, 2));
        List<String> successiveKeys = java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.products().stream())
                .map(product -> product.key())
                .toList();
        List<String> expectedKeys = new ExactProductGroupingService().group(candidates).stream()
                .map(product -> product.key())
                .toList();

        assertThat(successiveKeys).containsExactlyElementsOf(expectedKeys).doesNotHaveDuplicates();
        assertThat(java.util.stream.Stream.of(first, second, third)
                .flatMap(page -> page.products().stream())
                .flatMap(product -> product.offers().stream())
                .filter(offer -> offer.key().equals(shared.offer().key())))
                .hasSize(1);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.hasMore()).isTrue();
        assertThat(third.hasMore()).isFalse();
    }

    @Test
    void boundsPublicDiagnosticsButCountsEveryWindowDecision() {
        ProductIdentityEvidence semantic = new ProductIdentityEvidence(
                ProductIdentityEvidenceKind.SEMANTIC,
                IdentityEvidenceStrength.SEMANTIC,
                9_900,
                List.of(new ExternalIdentifier(
                        ExternalIdentifierType.SEMANTIC_FINGERPRINT,
                        "MEASURED",
                        "redacted-fingerprint"
                )),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "measured-similarity-v1",
                        null
                )
        );
        List<ProductCandidate> candidates = pageCandidates("diagnostic", 100).stream()
                .map(candidate -> withEvidence(candidate, semantic))
                .toList();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                new PagingPreparationService(),
                new StubFederatedDiscoveryService(new FederatedCatalogDiscoveryResult(
                        CatalogDiscoveryTerminalStatus.SUCCESS, List.of(), candidates, false)),
                new ExactProductGroupingService(new ProductGroupingMetrics(registry)),
                ProductRankingTestFactory.service(),
                new StubRankingContextFactory(),
                sessionStore(),
                referencePersistence(),
                new UserProductPreferenceMatchCuratorService()
        );

        var result = service.search(profile(), command(profile().id(), 0, 20));

        assertThat(result.groupingDecisionCount()).isEqualTo(4_950);
        assertThat(result.groupingDecisions()).hasSize(UserGroupedProductSearchService.MAX_PUBLIC_GROUPING_DECISIONS);
        assertThat(result.groupingDecisionsTruncated()).isTrue();
        assertThat(registry.get("commerce.catalog.grouping.decisions")
                .tags("outcome", "separate", "reason", "semantic_evidence_only")
                .counter().count()).isEqualTo(4_950d);
    }

    @Test
    void shopifyUpstreamTruncationNeverAdvertisesAnUnreachableContinuation() {
        List<ProductCandidate> candidates = pageCandidates("shopify-upstream", 50);
        DiscoverySourceIdentity shopify = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL"
        );
        CatalogSourceResult source = new CatalogSourceResult(
                shopify.provider(),
                shopify,
                CatalogSourceOperation.SEARCH,
                "2026-04-08",
                NegotiatedCapabilities.none(),
                candidates,
                null,
                true,
                null
        );
        StubFederatedDiscoveryService discovery = new StubFederatedDiscoveryService(
                new FederatedCatalogDiscoveryResult(
                        CatalogDiscoveryTerminalStatus.SUCCESS,
                        List.of(source),
                        candidates,
                        true
                )
        );
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                new PagingPreparationService(),
                discovery,
                new ExactProductGroupingService(),
                ProductRankingTestFactory.service(),
                new StubRankingContextFactory(),
                sessionStore(),
                referencePersistence(),
                new UserProductPreferenceMatchCuratorService()
        );

        var result = service.search(profile(), command(profile().id(), 40, 20));

        assertThat(result.products()).hasSize(10);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextOffset()).isNull();
        assertThat(result.upstreamTruncated()).isTrue();
        assertThat(result.sourceStates()).singleElement().satisfies(state -> {
            assertThat(state.truncated()).isTrue();
            assertThat(state.degraded()).isFalse();
        });
        assertThat(discovery.calls).isEqualTo(1);
        assertThat(discovery.request.candidateLimit()).isEqualTo(100);
    }

    private UserProductSearchPreparation preparation() {
        return new UserProductSearchPreparation(
                "linen shirt",
                null,
                null,
                null,
                new UserProductSearchCatalogInput("linen shirt", "normalized", null, null, null),
                "normalized",
                "profile-hash",
                Instant.parse("2026-07-11T00:00:00Z"),
                0,
                20,
                21
        );
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "shopper@example.com",
                "Shopper",
                null
        );
    }

    private SearchUserProductsCommand command(UUID userId) {
        return command(userId, 0, 20);
    }

    private SearchUserProductsCommand command(UUID userId, int offset, int limit) {
        return new SearchUserProductsCommand(
                userId, "linen shirt", null, "127.0.0.1", "test", offset, limit);
    }

    private UserCanonicalProductCandidateMapper candidateMapper() {
        return new UserCanonicalProductCandidateMapper(List.of(new ShopifyOfferIdentityStrategy()));
    }

    private UserProductSearchProductResult flatProduct(String priceCurrency, String selectedVariantPriceCurrency) {
        return flatProduct(priceCurrency, selectedVariantPriceCurrency, "legacy");
    }

    private UserProductSearchProductResult flatProduct(
            String priceCurrency,
            String selectedVariantPriceCurrency,
            String suffix
    ) {
        UUID merchantId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        boolean legacy = "legacy".equals(suffix);
        String productId = legacy ? "200" : suffix;
        String variantId = legacy ? "300" : suffix;
        String title = legacy ? "Linen shirt" : "Product " + suffix;
        String productPath = legacy ? "linen-shirt" : suffix;
        return new UserProductSearchProductResult(
                "legacy.example:" + suffix, "legacy-hash", merchantId, "shop.example", "Legacy merchant",
                "https://shop.example/mcp", 1, 0.9, 0.8, "gid://shopify/Product/" + productId, title,
                "<p>A linen shirt</p>", "https://shop.example/products/" + productPath,
                "https://shop.example/linen-shirt.jpg", 4200L, 4200L, priceCurrency, null, null, null, null,
                List.of(), List.of(), List.of("OEKO-TEX"), List.of("linen"), List.of(), List.of(), List.of(), true,
                null, "A linen shirt", "https://shop.example/linen-shirt.jpg", "42.00", "42.00", "usd",
                "gid://shopify/ProductVariant/" + variantId, "Natural / Medium", "42.00", selectedVariantPriceCurrency,
                "https://shop.example/linen-shirt.jpg", "Linen shirt", true, 1, 0.8, 1, 90, "Matches",
                List.of(), List.of(), null, null, null
        );
    }

    private List<ProductCandidate> pageCandidates(String prefix, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    UserProductSearchProductResult product = flatProduct(
                            "USD",
                            "USD",
                            prefix + "-" + index
                    );
                    return candidateMapper().from(
                            product,
                            integration(product.merchantId()),
                            Instant.parse("2026-07-10T10:00:00Z"),
                            ResultSourceType.MERCHANT_STOREFRONT
                    );
                })
                .toList();
    }

    private ProductCandidate withEvidence(ProductCandidate candidate, ProductIdentityEvidence evidence) {
        return new ProductCandidate(
                candidate.title(),
                candidate.description(),
                candidate.media(),
                candidate.attributes(),
                candidate.materials(),
                candidate.certifications(),
                candidate.attribution(),
                List.of(evidence),
                candidate.provenance(),
                candidate.offer()
        );
    }

    private UserGroupedProductSearchService pagingService(CatalogDiscoverySource... sources) {
        FederatedCatalogDiscoveryService discoveryService = new FederatedCatalogDiscoveryService(
                List.of(sources),
                new FederatedCatalogDiscoveryProperties(Duration.ofSeconds(1)),
                new FederatedCatalogDiscoveryMetrics(new SimpleMeterRegistry()),
                DEADLINE_SCHEDULER
        );
        return new UserGroupedProductSearchService(
                new PagingPreparationService(),
                discoveryService,
                new ExactProductGroupingService(),
                ProductRankingTestFactory.service(),
                new StubRankingContextFactory(),
                sessionStore(),
                referencePersistence(),
                new UserProductPreferenceMatchCuratorService()
        );
    }

    private static UserCanonicalProductSessionStore sessionStore() {
        return new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
    }

    private static UserCanonicalProductReferencePersistenceService referencePersistence() {
        return new UserCanonicalProductReferencePersistenceService(null, null, null, List.of()) {
            @Override
            public void replace(UUID userId, List<CanonicalProduct> products) {
            }
        };
    }

    private static final class StubRankingContextFactory extends UserProductRankingContextFactory {
        private StubRankingContextFactory() {
            super(null);
        }

        @Override
        public ProductRankingContext create(
                UUID userId,
                UserProductSearchPreparation preparation,
                List<CanonicalProduct> products
        ) {
            return new ProductRankingContext(
                    preparation.catalogInput().searchQuery(),
                    new CatalogSearchContext(null, null, null, "en", "USD", null),
                    preparation.catalogInput().filters(),
                    List.of(),
                    java.util.Map.of(),
                    preparation.now(),
                    100
            );
        }
    }

    private MerchantIntegrationResult integration(UUID merchantId) {
        Instant now = Instant.parse("2026-07-10T10:00:00Z");
        return new MerchantIntegrationResult(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                merchantId,
                MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                "gid://shopify/Shop/100",
                "shop.example",
                "shop.myshopify.com",
                "https://shop.example/mcp",
                "2026-04-08",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                now,
                now,
                now
        );
    }

    private static final class PagingPreparationService extends UserProductSearchPreparationService {

        private PagingPreparationService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public UserProductSearchPreparation prepare(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand command
        ) {
            return new UserProductSearchPreparation(
                    command.query(),
                    null,
                    null,
                    null,
                    new UserProductSearchCatalogInput(command.query(), "normalized", null, null, null),
                    "normalized",
                    "profile-hash",
                    Instant.parse("2026-07-11T00:00:00Z"),
                    command.offset(),
                    command.limit(),
                    command.offset() + command.limit() + 1
            );
        }
    }

    private static final class PrefixSource implements CatalogDiscoverySource {

        private final DiscoverySourceIdentity source;
        private final List<ProductCandidate> candidates;
        private final boolean fail;

        private PrefixSource(
                String provider,
                ResultSourceType type,
                String value,
                List<ProductCandidate> candidates,
                boolean fail
        ) {
            this.source = new DiscoverySourceIdentity(new ProviderIdentity(provider), type, value);
            this.candidates = candidates;
            this.fail = fail;
        }

        @Override
        public DiscoverySourceIdentity sourceIdentity() {
            return source;
        }

        @Override
        public Duration timeout() {
            return Duration.ofSeconds(1);
        }

        @Override
        public CatalogSourceResult search(
                CatalogDiscoveryRequest request,
                Consumer<ProductCandidate> candidateConsumer
        ) {
            if (fail) {
                return new CatalogSourceResult(
                        source.provider(),
                        source,
                        CatalogSourceOperation.SEARCH,
                        null,
                        NegotiatedCapabilities.none(),
                        List.of(),
                        null,
                        false,
                        new CatalogSourceFailure(
                                CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                                "Safe failure",
                                null,
                                null
                        )
                );
            }
            List<ProductCandidate> page = candidates.stream()
                    .limit(request.candidateLimit())
                    .toList();
            page.forEach(candidateConsumer);
            return new CatalogSourceResult(
                    source.provider(),
                    source,
                    CatalogSourceOperation.SEARCH,
                    "2026-04-08",
                    NegotiatedCapabilities.none(),
                    page,
                    null,
                    candidates.size() > page.size(),
                    null
            );
        }
    }

    private static final class StubPreparationService extends UserProductSearchPreparationService {

        private final UserProductSearchPreparation preparation;
        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;

        private StubPreparationService(UserProductSearchPreparation preparation) {
            super(null, null, null, null, null, null);
            this.preparation = preparation;
        }

        @Override
        public UserProductSearchPreparation prepare(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand command
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = command;
            return preparation;
        }
    }

    private static final class StubFederatedDiscoveryService extends FederatedCatalogDiscoveryService {

        private final FederatedCatalogDiscoveryResult result;
        private CatalogDiscoveryRequest request;
        private int calls;

        private StubFederatedDiscoveryService(FederatedCatalogDiscoveryResult result) {
            super(
                    List.of(),
                    new FederatedCatalogDiscoveryProperties(Duration.ofSeconds(1)),
                    new FederatedCatalogDiscoveryMetrics(new SimpleMeterRegistry()),
                    DEADLINE_SCHEDULER
            );
            this.result = result;
        }

        @Override
        public FederatedCatalogDiscoveryResult search(CatalogDiscoveryRequest request) {
            calls++;
            this.request = request;
            return result;
        }
    }
}
