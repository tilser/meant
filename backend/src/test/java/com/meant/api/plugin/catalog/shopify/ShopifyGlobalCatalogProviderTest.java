package com.meant.api.plugin.catalog.shopify;

import static org.assertj.core.api.Assertions.assertThat;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.GetMerchantIntegrationByProviderIdentityQuery;
import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.ExactProductGroupingService;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogContext;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogGetProductRequest;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogSearchRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.client.ShopifyUcpClient;
import com.meant.api.plugin.transport.client.ShopifyUcpTransportException;
import com.meant.api.plugin.transport.client.ShopifyUcpTransportFailure;
import com.meant.api.plugin.transport.dto.ShopifyUcpRequestOptions;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ShopifyGlobalCatalogProviderTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-07-10T10:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchCallsGlobalCatalogOnceNormalizesSellerOffersAndIgnoresUnknownExtensions() throws Exception {
        CapturingClient client = new CapturingClient(response(globalResponse()));
        ShopifyGlobalCatalogProvider provider = provider(client, properties(3));

        var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                "trail running shoes",
                new ShopifyCatalogContext("US", null, null, "en", "USD", "marathon training"),
                null,
                null,
                "cursor-in"
        ));

        assertThat(result.successful()).isTrue();
        assertThat(client.calls).hasSize(1);
        assertThat(client.calls.getFirst().toolName()).isEqualTo("search_catalog");
        assertThat(client.calls.getFirst().options().endpoint()).isEqualTo(properties(3).endpoint());
        assertThat(client.calls.getFirst().options().requiredScopes())
                .containsExactly("read_global_api_catalog_search");
        assertThat(client.calls.getFirst().options().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(client.calls.getFirst().options().readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(client.calls.getFirst().options().requestDeadline()).isEqualTo(Duration.ofSeconds(3));
        ShopifyGlobalCatalogArguments arguments = (ShopifyGlobalCatalogArguments) client.calls.getFirst().arguments();
        assertThat(arguments.catalog().pagination().limit()).isEqualTo(10);
        assertThat(arguments.catalog().pagination().cursor()).isEqualTo("cursor-in");
        assertThat(arguments.catalog().view()).isEqualTo("offer");

        assertThat(result.discoverySource().value()).isEqualTo("SHOPIFY_GLOBAL_CATALOG");
        assertThat(result.protocolVersion()).isEqualTo("2026-04-08");
        assertThat(result.page().cursor()).isEqualTo("next");
        assertThat(result.page().hasNextPage()).isTrue();
        assertThat(result.page().estimatedTotalCount()).isEqualTo(100);
        assertThat(result.negotiatedCapabilities().version(ShopifyGlobalCatalogExtensionCapabilityId.ID))
                .contains("2026-04-08");
        assertThat(result.candidates()).hasSize(2).allSatisfy(candidate -> {
            assertThat(candidate.offer().identity().provider().value()).isEqualTo("SHOPIFY");
            assertThat(candidate.offer().provenance()).singleElement()
                    .satisfies(provenance -> {
                        assertThat(provenance.discoverySource().type()).isEqualTo(ResultSourceType.PROVIDER_CATALOG);
                        assertThat(provenance.localRouting()).isNull();
                    });
            assertThat(candidate.identityEvidence()).singleElement()
                    .satisfies(evidence -> assertThat(evidence.trustedExact()).isTrue());
            assertThat(candidate.offer().checkoutUrl()).isNotNull();
            assertThat(candidate.attribution()).extracting(value -> value.label())
                    .contains("Product", "Merchant");
        });
        assertThat(result.candidates()).filteredOn(candidate -> "Seller One".equals(candidate.offer().merchantName()))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.attribution()).extracting(value -> value.label())
                        .contains("Merchant refund_policy"));

        CanonicalProduct grouped = new ExactProductGroupingService().group(result.candidates()).getFirst();
        assertThat(grouped.offers()).hasSize(2);
        assertThat(grouped.identityEvidence()).singleElement()
                .satisfies(evidence -> assertThat(evidence.identifiers().getFirst().value())
                        .isEqualTo("gid://shopify/p/upid-1"));
    }

    @Test
    void globalAndStorefrontObservationsOfSameExternalOfferMergeProvenance() throws Exception {
        ShopifyGlobalCatalogProvider provider = provider(
                new CapturingClient(response(globalResponse())),
                properties(3)
        );
        ProductCandidate global = provider.searchCatalog(
                new ShopifyGlobalCatalogSearchRequest("shoe", null, null)
        ).candidates().getFirst();
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        ResultProvenance storefrontProvenance = new ResultProvenance(
                global.offer().identity().provider(),
                new DiscoverySourceIdentity(
                        global.offer().identity().provider(),
                        ResultSourceType.MERCHANT_STOREFRONT,
                        global.offer().identity().merchantScope().externalMerchantIdentity().value()
                ),
                new LocalMerchantRouting(integrationId),
                global.offer().provenance().getFirst().externalMerchantReference(),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        global.offer().identity().provider().value(),
                        "gid://shopify/Product/merchant-product-1"
                ),
                global.offer().provenance().getFirst().externalVariantReference(),
                new ResultFreshness(OBSERVED_AT.plusSeconds(30), null),
                new ResultSourceReference(
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "gid://shopify/Shop/1",
                        URI.create("https://seller-one.test/api/ucp/mcp")
                )
        );
        OfferIdentity storefrontIdentity = new OfferIdentity(
                global.offer().identity().provider(),
                global.offer().identity().merchantScope(),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        global.offer().identity().provider().value(),
                        ShopifyOfferIdentity.productAnchor(
                                "gid://shopify/Product/merchant-product-1",
                                global.offer().identity().externalVariantIdentity().value()
                        )
                ),
                global.offer().identity().externalVariantIdentity(),
                global.offer().identity().selectedOptions(),
                global.offer().identity().components(),
                global.offer().identity().sellingPlanIdentity()
        );
        Offer storefrontOffer = new Offer(
                storefrontIdentity,
                global.offer().merchantName(),
                global.offer().variantTitle(),
                new Money(8500, "USD"),
                global.offer().listPrice(),
                global.offer().availability(),
                global.offer().delivery(),
                global.offer().checkoutUrl(),
                List.of(storefrontProvenance)
        );
        ProductCandidate storefront = new ProductCandidate(
                global.title(), global.description(), global.media(), global.attributes(), global.materials(),
                global.certifications(), global.attribution(), global.identityEvidence(),
                List.of(storefrontProvenance), storefrontOffer
        );

        assertThat(global.offer().key()).isEqualTo(storefront.offer().key());
        CanonicalProduct grouped = new ExactProductGroupingService().group(List.of(global, storefront)).getFirst();
        assertThat(grouped.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.provenance()).hasSize(2);
            assertThat(offer.provenance()).filteredOn(value -> value.localRouting() != null)
                    .singleElement()
                    .satisfies(value -> assertThat(value.localRouting().merchantIntegrationId()).isEqualTo(integrationId));
        });
    }

    @Test
    void lookupAndGetProductUseDeterministicToolsWithoutSearchFanout() throws Exception {
        CapturingClient client = new CapturingClient(Map.of(
                "lookup_catalog", response(globalResponse()),
                "get_product", response(fixture("get-product-success.json"))
        ));
        ShopifyGlobalCatalogProvider provider = provider(client, properties(3));

        var lookup = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(
                List.of("gid://shopify/ProductVariant/variant-1", "gid://shopify/ProductVariant/variant-1"),
                null,
                null
        ));
        var getProduct = provider.getProduct(new ShopifyGlobalCatalogGetProductRequest(
                "gid://shopify/p/upid-1",
                List.of(),
                List.of("Color", "Size"),
                null,
                null
        ));

        assertThat(lookup.successful()).isTrue();
        assertThat(getProduct.successful()).isTrue();
        assertThat(client.calls).extracting(Call::toolName)
                .containsExactly("lookup_catalog", "get_product");
        ShopifyGlobalCatalogArguments lookupArguments = (ShopifyGlobalCatalogArguments) client.calls.get(0).arguments();
        assertThat(lookupArguments.catalog().ids())
                .containsExactly("gid://shopify/ProductVariant/variant-1");
    }

    @Test
    void preservesVariantOptionComponentAndSellingPlanIdentity() throws Exception {
        ShopifyGlobalCatalogProvider provider = provider(
                new CapturingClient(response(configurationResponse())),
                properties(3)
        );

        var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("bundle", null, null));
        CanonicalProduct grouped = new ExactProductGroupingService().group(result.candidates()).getFirst();

        assertThat(result.successful()).isTrue();
        assertThat(grouped.offers()).hasSize(3);
        assertThat(grouped.offers()).extracting(Offer::key).doesNotHaveDuplicates();
        assertThat(grouped.offers()).extracting(offer -> offer.identity().components()).allSatisfy(components ->
                assertThat(components).hasSize(1));
        assertThat(grouped.offers()).extracting(Offer::sellingPlanIdentity).doesNotContainNull();
    }

    @Test
    void classifiesRateLimitAndOpensCircuitAfterConfiguredTransientFailures() {
        CapturingClient rateLimited = new CapturingClient(new ShopifyUcpTransportException(
                ShopifyUcpTransportFailure.RATE_LIMITED,
                "Shopify Global Catalog rate limited the request",
                Duration.ofSeconds(11),
                429,
                null
        ));
        var rateLimitResult = provider(rateLimited, properties(3))
                .searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));

        assertThat(rateLimitResult.failure().kind()).isEqualTo(CatalogSourceFailureKind.RATE_LIMITED);
        assertThat(rateLimitResult.failure().retryAfter()).isEqualTo(Duration.ofSeconds(11));
        assertThat(rateLimitResult.failure().upstreamStatus()).isEqualTo(429);

        CapturingClient transientFailure = new CapturingClient(new ShopifyUcpTransportException(
                ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                "Shopify Global Catalog returned a server failure",
                null,
                503,
                null
        ));
        ShopifyGlobalCatalogProvider provider = provider(transientFailure, properties(2));
        var first = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));
        var second = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));
        var circuitOpen = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));

        assertThat(first.failure().kind()).isEqualTo(CatalogSourceFailureKind.TRANSIENT_UPSTREAM);
        assertThat(second.failure().kind()).isEqualTo(CatalogSourceFailureKind.TRANSIENT_UPSTREAM);
        assertThat(circuitOpen.failure().kind()).isEqualTo(CatalogSourceFailureKind.UNAVAILABLE);
        assertThat(transientFailure.calls).hasSize(2);
    }

    @Test
    void classifiesMalformedNegotiationAndBoundsLookupAndSearchLimits() throws Exception {
        CapturingClient malformed = new CapturingClient(response("""
                {"ucp":{"version":"2026-04-08","capabilities":{}},"products":[]}
                """));
        var malformedResult = provider(malformed, properties(3))
                .searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));

        assertThat(malformedResult.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);

        CapturingClient bounded = new CapturingClient(response(globalResponse()));
        ShopifyGlobalCatalogProvider provider = provider(bounded, properties(3));
        provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null, 500, null));
        ShopifyGlobalCatalogArguments arguments = (ShopifyGlobalCatalogArguments) bounded.calls.getFirst().arguments();
        assertThat(arguments.catalog().pagination().limit()).isEqualTo(50);

        List<String> tooManyIds = java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> "gid://shopify/ProductVariant/" + index)
                .toList();
        var invalidLookup = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(tooManyIds, null, null));
        assertThat(invalidLookup.failure().kind()).isEqualTo(CatalogSourceFailureKind.INVALID_REQUEST);
        assertThat(bounded.calls).hasSize(1);
    }

    @Test
    void rejectsUnsupportedProtocolVersionsAndMissingRequiredOfferFields() throws Exception {
        String unsupportedVersion = globalResponse().replace(
                "\"version\": \"2026-04-08\"",
                "\"version\": \"2026-01-23\""
        );
        var unsupported = provider(
                new CapturingClient(response(unsupportedVersion)),
                properties(3)
        ).searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));
        assertThat(unsupported.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);

        String missingSeller = """
                {
                  "ucp":{"version":"2026-04-08","capabilities":{
                    "dev.ucp.shopping.catalog.search":[{"version":"2026-04-08"}],
                    "dev.shopify.catalog.global":[{"version":"2026-04-08"}]
                  }},
                  "products":[{
                    "id":"gid://shopify/p/required-fields",
                    "title":"Required fields",
                    "variants":[{
                      "id":"gid://shopify/ProductVariant/1",
                      "title":"Missing seller",
                      "price":{"amount":1000,"currency":"USD"}
                    }]
                  }]
                }
                """;
        var missingRequiredField = provider(
                new CapturingClient(response(missingSeller)),
                properties(3)
        ).searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));
        assertThat(missingRequiredField.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);

        String missingGetProduct = """
                {
                  "ucp":{"version":"2026-04-08","capabilities":{
                    "dev.ucp.shopping.catalog.lookup":[{"version":"2026-04-08"}],
                    "dev.shopify.catalog.global":[{"version":"2026-04-08"}]
                  }}
                }
                """;
        var missingProduct = provider(
                new CapturingClient(response(missingGetProduct)),
                properties(3)
        ).getProduct(new ShopifyGlobalCatalogGetProductRequest(
                "gid://shopify/p/required-product", null, null, null, null
        ));
        assertThat(missingProduct.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);
    }

    @Test
    void advertisesConfiguredGlobalExtensionVersionSchemaAndParents() {
        ShopifyGlobalCatalogProperties properties = properties(3);
        ShopifyGlobalCatalogExtensionCapability capability = new ShopifyGlobalCatalogExtensionCapability(properties);

        assertThat(capability.id().value()).isEqualTo(properties.extensionId());
        assertThat(capability.advertisements()).singleElement().satisfies(advertisement -> {
            assertThat(advertisement.version()).isEqualTo("2026-04-08");
            assertThat(advertisement.protocolVersions().min()).isEqualTo("2026-04-08");
            assertThat(advertisement.spec()).isEqualTo(properties.extensionSpec());
            assertThat(advertisement.schema()).isEqualTo(properties.extensionSchema());
            assertThat(advertisement.extendsCapabilities())
                    .containsExactlyInAnyOrder(
                            com.meant.api.plugin.catalog.search.CatalogSearchCapability.ID,
                            com.meant.api.plugin.catalog.lookup.CatalogLookupCapability.ID
                    );
        });
    }

    @Test
    void attachesKnownLocalRoutingOnlyAsOptionalProvenance() throws Exception {
        ShopifyGlobalCatalogProperties properties = properties(3);
        UUID integrationId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        MerchantIntegrationResult integration = integration(integrationId, "gid://shopify/Shop/1");
        MerchantIntegrationLookupService lookup = new StubMerchantIntegrationLookupService(
                Map.of("gid://shopify/Shop/1", integration)
        );
        ShopifyGlobalCatalogResponseParser parser = new ShopifyGlobalCatalogResponseParser(objectMapper, properties);
        var parsed = parser.parse(
                response(globalResponse()),
                com.meant.api.plugin.catalog.search.CatalogSearchCapability.ID
        );
        ShopifyGlobalCatalogNormalizer normalizer = new ShopifyGlobalCatalogNormalizer(
                lookup,
                properties,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );

        List<ProductCandidate> candidates = normalizer.normalize(parsed.payload()).candidates();

        assertThat(candidates).filteredOn(candidate -> candidate.offer().merchantName().equals("Seller One"))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.offer().identity().merchantScope().externalMerchantIdentity()).isNotNull();
                    assertThat(candidate.offer().provenance().getFirst().localRouting().merchantIntegrationId())
                            .isEqualTo(integrationId);
                });
        assertThat(candidates).filteredOn(candidate -> candidate.offer().merchantName().equals("Seller Two"))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.offer().provenance().getFirst().localRouting()).isNull());
    }

    @Test
    void boundsConcurrentCallsToTheSharedUpstream() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        UcpToolResponse response = response(globalResponse());
        ShopifyUcpClient blockingClient = (options, toolName, arguments) -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release test catalog call");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted test catalog call", exception);
            }
            return response;
        };
        ShopifyGlobalCatalogProvider provider = provider(blockingClient, properties(3));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> provider.searchCatalog(
                    new ShopifyGlobalCatalogSearchRequest("first", null, null)));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            var capacityFailure = provider.searchCatalog(
                    new ShopifyGlobalCatalogSearchRequest("second", null, null));

            assertThat(capacityFailure.failure().kind()).isEqualTo(CatalogSourceFailureKind.UNAVAILABLE);
            assertThat(capacityFailure.failure().message()).contains("capacity");
            assertThat(calls).hasValue(1);
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS).successful()).isTrue();
        }
    }

    private ShopifyGlobalCatalogProvider provider(ShopifyUcpClient client, ShopifyGlobalCatalogProperties properties) {
        MerchantIntegrationLookupService lookup = new StubMerchantIntegrationLookupService(Map.of());
        ShopifyGlobalCatalogResponseParser parser = new ShopifyGlobalCatalogResponseParser(objectMapper, properties);
        ShopifyGlobalCatalogNormalizer normalizer = new ShopifyGlobalCatalogNormalizer(
                lookup,
                properties,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        ShopifyGlobalCatalogCircuitBreaker circuitBreaker = new ShopifyGlobalCatalogCircuitBreaker(
                properties.circuitFailureThreshold(),
                properties.circuitOpenDuration(),
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        return new ShopifyGlobalCatalogProvider(client, parser, normalizer, circuitBreaker, properties);
    }

    private ShopifyGlobalCatalogProperties properties(int failureThreshold) {
        return new ShopifyGlobalCatalogProperties(
                URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "SHOPIFY_GLOBAL_CATALOG",
                "2026-04-08",
                "dev.shopify.catalog.global",
                "2026-04-08",
                URI.create("https://shopify.dev/docs/agents/catalog/global-catalog"),
                URI.create("https://shopify.dev/ucp/schemas/2026-04-08/shopify_catalog_global.json"),
                Set.of("read_global_api_catalog_search"),
                "offer",
                10,
                50,
                50,
                200,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                failureThreshold,
                Duration.ofSeconds(30)
        );
    }

    private UcpToolResponse response(String json) throws Exception {
        return new UcpToolResponse(null, objectMapper.readTree(json), NegotiatedCapabilities.none());
    }

    private String globalResponse() {
        return fixture("search-success.json");
    }

    private String configurationResponse() {
        return fixture("configuration-identity.json");
    }

    private String fixture(String name) {
        try (var input = getClass().getResourceAsStream("/fixtures/shopify-global-catalog/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing Shopify contract fixture " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read Shopify contract fixture " + name, exception);
        }
    }

    private MerchantIntegrationResult integration(UUID id, String externalMerchantId) {
        return new MerchantIntegrationResult(
                id,
                null,
                MerchantIntegrationProvider.SHOPIFY,
                null,
                Set.of(),
                externalMerchantId,
                null,
                null,
                null,
                "2026-04-08",
                null,
                MerchantIntegrationStatus.ACTIVE,
                null,
                OBSERVED_AT,
                OBSERVED_AT,
                OBSERVED_AT
        );
    }

    private static final class ShopifyGlobalCatalogExtensionCapabilityId {
        private static final com.meant.api.plugin.spi.CapabilityId ID =
                com.meant.api.plugin.spi.CapabilityId.of("dev.shopify.catalog.global");
    }

    private static final class CapturingClient implements ShopifyUcpClient {

        private final Map<String, UcpToolResponse> responses;
        private final ShopifyUcpTransportException exception;
        private final List<Call> calls = new ArrayList<>();

        private CapturingClient(UcpToolResponse response) {
            this.responses = Map.of("*", response);
            this.exception = null;
        }

        private CapturingClient(Map<String, UcpToolResponse> responses) {
            this.responses = Map.copyOf(responses);
            this.exception = null;
        }

        private CapturingClient(ShopifyUcpTransportException exception) {
            this.responses = Map.of();
            this.exception = exception;
        }

        @Override
        public UcpToolResponse callTool(ShopifyUcpRequestOptions options, String toolName, Object arguments) {
            calls.add(new Call(options, toolName, arguments));
            if (exception != null) {
                throw exception;
            }
            return responses.getOrDefault(toolName, responses.get("*"));
        }
    }

    private static final class StubMerchantIntegrationLookupService extends MerchantIntegrationLookupService {

        private final Map<String, MerchantIntegrationResult> integrations;

        private StubMerchantIntegrationLookupService(Map<String, MerchantIntegrationResult> integrations) {
            super(null);
            this.integrations = integrations;
        }

        @Override
        public Optional<MerchantIntegrationResult> findByProviderIdentity(
                GetMerchantIntegrationByProviderIdentityQuery query
        ) {
            return Optional.ofNullable(integrations.get(query.externalMerchantId()));
        }
    }

    private record Call(ShopifyUcpRequestOptions options, String toolName, Object arguments) {
    }
}
