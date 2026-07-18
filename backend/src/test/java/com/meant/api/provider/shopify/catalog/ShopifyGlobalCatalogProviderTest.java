package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.ExactProductGroupingService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyGlobalCatalogExtensionCapability;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyGlobalCatalogExtensionProperties;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.auth.ShopifyUcpClient;
import com.meant.api.provider.shopify.auth.ShopifyUcpRequestOptions;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportException;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportFailure;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogItemReference;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogArguments;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogGetProductRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
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
            assertThat(candidate.retrievalSignals()).singleElement().satisfies(signal -> {
                assertThat(signal.valueBasisPoints()).isBetween(0, 10_000);
                assertThat(signal.calibrationVersion()).isEqualTo("shopify-global-catalog-ordinal-v1");
                assertThat(signal.merchantScope()).isSameAs(candidate.offer().identity().merchantScope());
            });
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
    void searchSerializesQueryAndOneProductLevelSimilarityReference() throws Exception {
        CapturingClient client = new CapturingClient(response(globalResponse()));
        ShopifyGlobalCatalogProvider provider = provider(client, properties(3));
        List<String> productIds = List.of(
                "gid://shopify/p/upid-1",
                "gid://shopify/Product/merchant-product-1"
        );

        for (String productId : productIds) {
            var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                    "black linen shirt",
                    new ShopifyCatalogItemReference(productId),
                    null,
                    null,
                    null,
                    null
            ));

            assertThat(result.successful()).isTrue();
        }

        assertThat(client.calls).hasSize(2);
        for (int index = 0; index < productIds.size(); index++) {
            String productId = productIds.get(index);
            ShopifyGlobalCatalogArguments arguments =
                    (ShopifyGlobalCatalogArguments) client.calls.get(index).arguments();
            assertThat(arguments.catalog().query()).isEqualTo("black linen shirt");
            assertThat(arguments.catalog().like()).singleElement()
                    .satisfies(reference -> assertThat(reference.id()).isEqualTo(productId));
            assertThat(objectMapper.writeValueAsString(arguments))
                    .contains("\"like\":[{\"id\":\"" + productId + "\"}]")
                    .doesNotContain("image");
        }
    }

    @Test
    void rejectsNonProductAndSyntheticSimilarityReferencesBeforeCallingShopify() throws Exception {
        CapturingClient client = new CapturingClient(response(globalResponse()));
        ShopifyGlobalCatalogProvider provider = provider(client, properties(3));

        for (String invalidId : List.of(
                "gid://shopify/ProductVariant/variant-1",
                "gid://shopify/MediaImage/image-1",
                "gid://shopify/Product/product-1/variant-1",
                "variant-product:v1:merchant:product"
        )) {
            var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                    "black linen shirt",
                    new ShopifyCatalogItemReference(invalidId),
                    null,
                    null,
                    null,
                    null
            ));

            assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.INVALID_REQUEST);
        }

        assertThat(client.calls).isEmpty();
    }

    @Test
    void acceptsScalarInferredMetadataValuesReturnedByShopify() throws Exception {
        String scalarMetadataResponse = globalResponse()
                .replace("\"tech_specs\": [\"250g\"]", "\"tech_specs\": \"250g\"")
                .replace(
                        "\"top_features\": [\"grippy sole\"]",
                        "\"top_features\": \"grippy sole\",\n"
                                + "      \"unique_selling_points\": \"Designed for technical trails\""
                );

        var result = provider(new CapturingClient(response(scalarMetadataResponse)), properties(3))
                .searchCatalog(new ShopifyGlobalCatalogSearchRequest("trail running shoes", null, null));

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).hasSize(2).allSatisfy(candidate ->
                assertThat(candidate.attributes()).extracting(attribute -> attribute.name(), attribute -> attribute.value())
                        .contains(
                                org.assertj.core.groups.Tuple.tuple("technical specification", "250g"),
                                org.assertj.core.groups.Tuple.tuple("top feature", "grippy sole"),
                                org.assertj.core.groups.Tuple.tuple(
                                        "unique selling point",
                                        "Designed for technical trails"
                                )
                        ));
    }

    @Test
    void failsClosedWhenShopifyIgnoresARequestedHardFilter() throws Exception {
        String ignoredFilterResponse = globalResponse().replace(
                "\"pagination\":",
                "\"messages\":[{\"type\":\"info\",\"code\":\"unsupported\","
                        + "\"path\":\"/catalog/filters/attributes/0\","
                        + "\"content\":\"Attribute Color was ignored.\"}],"
                        + "\"pagination\":"
        );
        ShopifyGlobalCatalogProvider provider = provider(
                new CapturingClient(response(ignoredFilterResponse)),
                properties(3)
        );

        var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                "trail running shoes",
                new ShopifyCatalogContext("US", null, null, "en", "USD", null),
                new ShopifyCatalogFilters(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new ShopifyCatalogFilters.Attribute("Color", List.of("Black"))),
                        null,
                        null
                )
        ));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.INVALID_REQUEST);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void failsClosedWhenPathlessMessageNamesAnIgnoredRequestedHardFilter() throws Exception {
        String ignoredFilterResponse = globalResponse().replace(
                "\"pagination\":",
                "\"messages\":[{\"type\":\"info\",\"code\":\"unsupported\","
                        + "\"content\":\"Attribute Color was ignored.\"}],"
                        + "\"pagination\":"
        );
        ShopifyGlobalCatalogProvider provider = provider(
                new CapturingClient(response(ignoredFilterResponse)),
                properties(3)
        );

        var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                "trail running shoes",
                new ShopifyCatalogContext("US", null, null, "en", "USD", null),
                new ShopifyCatalogFilters(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new ShopifyCatalogFilters.Attribute("Color", List.of("Black"))),
                        null,
                        null
                )
        ));

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.INVALID_REQUEST);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void doesNotTreatAnUnrequestedPathlessAttributeAsAnIgnoredHardFilter() throws Exception {
        String ignoredPreferenceResponse = globalResponse().replace(
                "\"pagination\":",
                "\"messages\":[{\"type\":\"info\",\"code\":\"unsupported\","
                        + "\"content\":\"Attribute Brand was ignored.\"}],"
                        + "\"pagination\":"
        );

        var result = provider(new CapturingClient(response(ignoredPreferenceResponse)), properties(3))
                .searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                        "trail running shoes",
                        null,
                        new ShopifyCatalogFilters(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(new ShopifyCatalogFilters.Attribute("Color", List.of("Black"))),
                                null,
                                null
                        )
                ));

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).isNotEmpty();
    }

    @Test
    void getProductKeepsPathlessInformationalMessagesThatDoNotIdentifyAnIgnoredFilter() throws Exception {
        String response = fixture("get-product-success.json")
                .replace("    \"path\": \"/product/variants/0\",\n", "")
                .replace("Runs true to size", "An optional preference was ignored; runs true to size");

        var result = provider(new CapturingClient(response(response)), properties(3))
                .getProductWithDetails(new ShopifyGlobalCatalogGetProductRequest(
                        "gid://shopify/ProductVariant/variant-1",
                        null,
                        List.of("Prefer a wide fit"),
                        null,
                        new ShopifyCatalogFilters(
                                false,
                                null,
                                null,
                                null,
                                null,
                                List.of("gid://shopify/Shop/1"),
                                null,
                                null,
                                null,
                                null
                        )));

        assertThat(result.catalogResult().successful()).isTrue();
        assertThat(result.product()).isNotNull();
        assertThat(result.messages()).singleElement()
                .satisfies(message -> assertThat(message.path()).isNull());
    }

    @Test
    void serializesEveryGlobalCatalogExtensionFilterWithTheDocumentedWireShape() throws Exception {
        CapturingClient client = new CapturingClient(response(globalResponse()));
        ShopifyGlobalCatalogProvider provider = provider(client, properties(3));
        ShopifyCatalogFilters filters = new ShopifyCatalogFilters(
                true,
                List.of("new"),
                new ShopifyCatalogFilters.Location("US", "CA", "90210"),
                List.of(new ShopifyCatalogFilters.Location("US", null, null)),
                new ShopifyCatalogFilters.Price(5_000L, 15_000L),
                List.of("gid://shopify/Shop/1"),
                List.of("gid://shopify/TaxonomyCategory/aa-8-1"),
                List.of(
                        new ShopifyCatalogFilters.Attribute("Color", List.of("Black")),
                        new ShopifyCatalogFilters.Attribute("Size", List.of("10", "10.5")),
                        new ShopifyCatalogFilters.Attribute("Target gender", List.of("Male"))
                ),
                new ShopifyCatalogFilters.Rating(
                        new ShopifyCatalogFilters.VariantRating(new BigDecimal("4.5"), 10L)
                ),
                List.of("low", "medium")
        );

        var result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                "trail running shoes",
                new ShopifyCatalogContext("US", "CA", "90210", "en", "USD", null),
                filters
        ));

        assertThat(result.successful()).isTrue();
        ShopifyGlobalCatalogArguments arguments = (ShopifyGlobalCatalogArguments) client.calls.getFirst().arguments();
        String json = objectMapper.writeValueAsString(arguments);
        assertThat(json)
                .contains("\"available\":true")
                .contains("\"condition\":[\"new\"]")
                .contains("\"ships_to\":{\"country\":\"US\",\"region\":\"CA\",\"postal_code\":\"90210\"}")
                .contains("\"ships_from\":[{\"country\":\"US\"}]")
                .contains("\"price\":{\"min\":5000,\"max\":15000}")
                .contains("\"shops\":[\"gid://shopify/Shop/1\"]")
                .contains("\"categories\":[\"gid://shopify/TaxonomyCategory/aa-8-1\"]")
                .contains("\"attributes\":[{\"name\":\"Color\",\"values\":[\"Black\"]}")
                .contains("{\"name\":\"Size\",\"values\":[\"10\",\"10.5\"]}")
                .contains("{\"name\":\"Target gender\",\"values\":[\"Male\"]}")
                .contains("\"rating\":{\"variant\":{\"min\":4.5,\"min_count\":10}}")
                 .contains("\"price_tier\":[\"low\",\"medium\"]")
                 .doesNotContain("\"taxonomy\"");
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
                ShopifyOfferIdentityStrategy.productIdentity(
                        global.offer().identity().provider(),
                        new ExternalIdentifier(
                                ExternalIdentifierType.PRODUCT,
                                global.offer().identity().provider().value(),
                                "gid://shopify/Product/merchant-product-1"
                        ),
                        global.offer().identity().externalVariantIdentity()
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
    void emitsTrustedBarcodeEvidenceOnlyForValidGs1Identifiers() throws Exception {
        String withBarcodes = globalResponse().replace(
                "\"availability\": {\"available\": true, \"status\": \"in_stock\"}",
                "\"availability\": {\"available\": true, \"status\": \"in_stock\"},"
                        + "\"barcodes\":["
                        + "{\"type\":\"UPC\",\"value\":\"036000291452\"},"
                        + "{\"type\":\"GTIN\",\"value\":\"00000000000000\"},"
                        + "{\"type\":\"EAN\",\"value\":\"4006381333930\"}]"
        );

        var result = provider(new CapturingClient(response(withBarcodes)), properties(3))
                .searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));

        assertThat(result.candidates()).hasSize(2).allSatisfy(candidate ->
                assertThat(candidate.identityEvidence()).extracting(evidence -> evidence.kind())
                        .containsExactly(
                                com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind.UPID,
                                com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind.UPC
                        ));
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
        var getProduct = provider.getProductWithDetails(new ShopifyGlobalCatalogGetProductRequest(
                "gid://shopify/p/upid-1",
                List.of(),
                List.of("Color", "Size"),
                null,
                null
        ));

        assertThat(lookup.successful()).isTrue();
        assertThat(getProduct.catalogResult().successful()).isTrue();
        assertThat(getProduct.product()).satisfies(product -> {
            assertThat(product.handle()).isEqualTo("trail-runner");
            assertThat(product.description().preferredText()).isEqualTo("Full trail runner detail");
            assertThat(product.options()).singleElement()
                    .satisfies(option -> assertThat(option.values()).singleElement()
                            .satisfies(value -> assertThat(value.label()).isEqualTo("Black")));
            assertThat(product.variants()).singleElement().satisfies(variant -> {
                assertThat(variant.sku()).isEqualTo("TR-BLK-42");
                assertThat(variant.handle()).isEqualTo("black-42");
                assertThat(variant.description().preferredText()).isEqualTo("Black trail runner variant");
                assertThat(variant.media()).singleElement()
                        .satisfies(media -> assertThat(media.url())
                                .isEqualTo("https://seller-one.example/trail-runner-black.jpg"));
            });
            assertThat(product.rating().value()).isEqualByComparingTo("4.8");
            assertThat(product.rating().count()).isEqualTo(246L);
            assertThat(product.metadata().techSpecs()).containsExactly("8 mm drop");
        });
        assertThat(getProduct.messages()).singleElement()
                .satisfies(message -> assertThat(message.content()).isEqualTo("Runs true to size"));
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
    void unexpectedHalfOpenProbeFailureDoesNotPermanentlyLockCircuit(CapturedOutput output) throws Exception {
        ShopifyGlobalCatalogProperties properties = properties(1);
        MutableClock clock = new MutableClock(OBSERVED_AT);
        ShopifyGlobalCatalogCircuitBreaker circuitBreaker = new ShopifyGlobalCatalogCircuitBreaker(
                properties.circuitFailureThreshold(),
                properties.circuitOpenDuration(),
                clock
        );
        AtomicInteger calls = new AtomicInteger();
        UcpToolResponse success = response(globalResponse());
        ShopifyUcpClient client = (options, toolName, arguments) -> switch (calls.getAndIncrement()) {
            case 0 -> throw new ShopifyUcpTransportException(
                    ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                    "Shopify Global Catalog returned a server failure",
                    null,
                    503,
                    null
            );
            case 1 -> throw new NullPointerException("unexpected provider bug");
            default -> success;
        };
        ShopifyGlobalCatalogProvider provider = provider(client, properties, circuitBreaker);

        assertThat(provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null))
                .failure().kind()).isEqualTo(CatalogSourceFailureKind.TRANSIENT_UPSTREAM);
        clock.advance(properties.circuitOpenDuration());
        assertThat(provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null))
                .failure().kind()).isEqualTo(CatalogSourceFailureKind.TRANSIENT_UPSTREAM);
        clock.advance(properties.circuitOpenDuration());

        assertThat(provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null)).successful())
                .isTrue();
        assertThat(calls).hasValue(3);
        assertThat(output).asString()
                .contains("exceptionType=java.lang.NullPointerException")
                .doesNotContain("unexpected provider bug");
    }

    @Test
    void ignoredHalfOpenFailureClosesTheUpstreamCircuit() {
        MutableClock clock = new MutableClock(OBSERVED_AT);
        ShopifyGlobalCatalogCircuitBreaker circuitBreaker = new ShopifyGlobalCatalogCircuitBreaker(
                1,
                Duration.ofSeconds(30),
                clock
        );
        circuitBreaker.recordFailure(null);
        clock.advance(Duration.ofSeconds(30));

        assertThat(circuitBreaker.tryAcquire()).isTrue();
        circuitBreaker.recordIgnoredFailure();

        assertThat(circuitBreaker.isOpen()).isFalse();
        assertThat(circuitBreaker.tryAcquire()).isTrue();
        assertThat(circuitBreaker.tryAcquire()).isTrue();
    }

    @Test
    void ignoredClosedCircuitFailurePreservesTransientFailureCount() {
        ShopifyGlobalCatalogCircuitBreaker circuitBreaker = new ShopifyGlobalCatalogCircuitBreaker(
                2,
                Duration.ofSeconds(30),
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        circuitBreaker.recordFailure(null);

        circuitBreaker.recordIgnoredFailure();
        circuitBreaker.recordFailure(null);

        assertThat(circuitBreaker.isOpen()).isTrue();
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

        String negativeListPrice = globalResponse().replace(
                "\"list_price\": {\"amount\": 9999, \"currency\": \"USD\"}",
                "\"list_price\": {\"amount\": -1, \"currency\": \"USD\"}"
        );
        var invalidListPrice = provider(
                new CapturingClient(response(negativeListPrice)),
                properties(3)
        ).searchCatalog(new ShopifyGlobalCatalogSearchRequest("shoe", null, null));
        assertThat(invalidListPrice.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);

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
        ShopifyGlobalCatalogExtensionCapability capability = new ShopifyGlobalCatalogExtensionCapability(
                new ShopifyGlobalCatalogExtensionProperties("2026-04-08")
        );

        assertThat(capability.id()).isEqualTo(ShopifyGlobalCatalogExtensionCapability.ID);
        assertThat(capability.advertisements()).singleElement().satisfies(advertisement -> {
            assertThat(advertisement.version()).isEqualTo("2026-04-08");
            assertThat(advertisement.protocolVersions().min()).isEqualTo("2026-04-08");
            assertThat(advertisement.spec()).isEqualTo(
                    URI.create("https://shopify.dev/docs/agents/catalog/global-catalog")
            );
            assertThat(advertisement.schema()).isEqualTo(
                    URI.create("https://shopify.dev/ucp/schemas/2026-04-08/shopify_catalog_global.json")
            );
            assertThat(advertisement.extendsCapabilities())
                    .containsExactlyInAnyOrder(
                            com.meant.api.plugin.catalog.search.CatalogSearchCapability.ID,
                            com.meant.api.plugin.catalog.lookup.CatalogLookupCapability.ID
                    );
        });
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
        ShopifyGlobalCatalogCircuitBreaker circuitBreaker = new ShopifyGlobalCatalogCircuitBreaker(
                properties.circuitFailureThreshold(),
                properties.circuitOpenDuration(),
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        return provider(client, properties, circuitBreaker);
    }

    private ShopifyGlobalCatalogProvider provider(
            ShopifyUcpClient client,
            ShopifyGlobalCatalogProperties properties,
            ShopifyGlobalCatalogCircuitBreaker circuitBreaker
    ) {
        ShopifyGlobalCatalogResponseParser parser = new ShopifyGlobalCatalogResponseParser(objectMapper, properties);
        ShopifyGlobalCatalogNormalizer normalizer = new ShopifyGlobalCatalogNormalizer(
                properties,
                Clock.fixed(OBSERVED_AT, ZoneOffset.UTC)
        );
        return new ShopifyGlobalCatalogProvider(client, parser, normalizer, circuitBreaker, properties);
    }

    private ShopifyGlobalCatalogProperties properties(int failureThreshold) {
        return new ShopifyGlobalCatalogProperties(
                true,
                URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
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

    private record Call(ShopifyUcpRequestOptions options, String toolName, Object arguments) {
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
