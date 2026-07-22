package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.controller.response.MerchantSemanticProductResponse;
import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.CatalogRating;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.dto.ProductSellingPlanGroup;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@ExtendWith(OutputCaptureExtension.class)
class MerchantSemanticProductSearchServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private FakeMerchantSemanticSearchService merchantSemanticSearchService;
    private FakeMerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService;
    private FakeVoyageRerankClient voyageRerankClient;
    private FakeMerchantLookupService merchantLookupService;
    private MerchantSemanticProductSearchService merchantSemanticProductSearchService;

    @BeforeEach
    void setUp() {
        merchantSemanticSearchService = new FakeMerchantSemanticSearchService();
        merchantCatalogPluginDispatchService = new FakeMerchantCatalogPluginDispatchService();
        voyageRerankClient = new FakeVoyageRerankClient();
        merchantLookupService = new FakeMerchantLookupService();
        ProductCatalogMetadataNormalizer metadataNormalizer = new ProductCatalogMetadataNormalizer();
        merchantSemanticProductSearchService = new MerchantSemanticProductSearchService(
                merchantSemanticSearchService,
                voyageRerankClient,
                merchantLookupService,
                new MerchantCatalogSearchProperties(3, 2, 2, 2),
                new MerchantCatalogSearchExecutor(merchantCatalogPluginDispatchService),
                new MerchantProductDetailsEnricher(
                        merchantCatalogPluginDispatchService,
                        new MerchantRichCatalogNormalizer(metadataNormalizer),
                        new ProductSellingPlanGroupMapper()
                ),
                new MerchantProductFilterMatcher(metadataNormalizer)
        );
    }

    @Test
    void searchesTopRerankedMerchantsAndReranksProductsAcrossCatalogs() {
        MerchantSemanticSearchResult homeMerchant = merchant("home.example", "Home Store", 1);
        MerchantSemanticSearchResult shoeMerchant = merchant("shoe.example", "Shoe Store", 2);
        MerchantSemanticSearchResult skippedMerchant = merchant("skipped.example", "Skipped Store", 3);
        merchantSemanticSearchService.results = List.of(homeMerchant, shoeMerchant, skippedMerchant);
        merchantCatalogPluginDispatchService.results.put(homeMerchant.domain(), catalogSearchResult(homeMerchant, List.of(
                product("home-lamp", "Modern Table Lamp", "A warm lamp for desks", "home-lighting"),
                product("home-sneaker-rack", "Entryway Shoe Rack", "Storage for running shoes", "home-storage")
        )));
        merchantCatalogPluginDispatchService.results.put(shoeMerchant.domain(), catalogSearchResult(shoeMerchant, List.of(
                product("trail-runner", "Trail Running Shoe", "Grip for long runs", "shoes"),
                product("casual-sneaker", "Casual Sneaker", "Everyday walking shoe", "shoes")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(merchantSemanticSearchService.lastQuery.limit()).isEqualTo(3);
        assertThat(merchantCatalogPluginDispatchService.catalogSearchCalls).containsExactlyInAnyOrder(
                "home.example:running shoes:2",
                "shoe.example:running shoes:2"
        );
        assertThat(voyageRerankClient.documents).hasSize(4);
        assertThat(result.merchants()).extracting("domain")
                .containsExactly("home.example", "shoe.example");
        assertThat(result.products()).extracting("productId")
                .containsExactly("trail-runner", "casual-sneaker");
        assertThat(merchantCatalogPluginDispatchService.lookupCalls).containsExactlyInAnyOrder(
                "shoe.example:trail-runner",
                "shoe.example:casual-sneaker"
        );
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactlyInAnyOrder(
                "shoe.example:trail-runner",
                "shoe.example:casual-sneaker"
        );
        assertThat(result.products()).extracting("rank")
                .containsExactly(1, 2);
        assertThat(result.products().getFirst().merchantDomain()).isEqualTo("shoe.example");
        assertThat(result.products().getFirst().selectedVariantId()).isEqualTo("trail-runner-selected");
        assertThat(result.products().getFirst().selectedVariantPriceAmount()).isEqualTo("12.95");
        assertThat(result.products().getFirst().productRerankScore()).isGreaterThan(result.products().getLast().productRerankScore());
    }

    @Test
    void keepsSuccessfulProductsWhenOneMerchantCatalogSearchFails() {
        MerchantSemanticSearchResult failingMerchant = merchant("failing.example", "Failing Store", 1);
        MerchantSemanticSearchResult workingMerchant = merchant("working.example", "Working Store", 2);
        merchantSemanticSearchService.results = List.of(failingMerchant, workingMerchant);
        merchantCatalogPluginDispatchService.failures.put(failingMerchant.domain(), "catalog unavailable");
        merchantCatalogPluginDispatchService.results.put(workingMerchant.domain(), catalogSearchResult(workingMerchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(result.merchants()).hasSize(2);
        assertThat(result.merchants().getFirst().error()).isEqualTo("Merchant catalog search failed");
        assertThat(result.merchants().getLast().productCount()).isEqualTo(1);
        assertThat(result.products()).extracting("productId").containsExactly("runner");
    }

    @Test
    void keepsSuccessfulProductsAndRedactsFailureDetailsWhenMerchantPluginThrowsRuntimeException(
            CapturedOutput output
    ) {
        MerchantSemanticSearchResult failingMerchant = merchant("failing.example", "Failing Store", 1);
        MerchantSemanticSearchResult workingMerchant = merchant("working.example", "Working Store", 2);
        merchantSemanticSearchService.results = List.of(failingMerchant, workingMerchant);
        String secretFailure = "Bearer secret-token product-payload";
        String privateQuery = "private-running-shoes-query";
        merchantCatalogPluginDispatchService.runtimeFailures.put(failingMerchant.domain(), secretFailure);
        merchantCatalogPluginDispatchService.results.put(workingMerchant.domain(), catalogSearchResult(workingMerchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));

        List<String> streamedProductIds = new CopyOnWriteArrayList<>();
        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(privateQuery, null, null, null, null, null),
                product -> streamedProductIds.add(product.productId() + ":" + product.selectedVariantId())
        );

        assertThat(result.merchants()).hasSize(2);
        assertThat(result.merchants().getFirst().error()).isEqualTo("Merchant catalog search failed");
        assertThat(result.merchants().getLast().productCount()).isEqualTo(1);
        assertThat(result.products()).extracting("productId").containsExactly("runner");
        assertThat(streamedProductIds).containsExactly("runner:runner-variant", "runner:runner-selected");
        assertThat(output.getAll()).doesNotContain(secretFailure, privateQuery, "product-payload", "secret-token");
    }

    @Test
    void keepsPartialProductWhenProductDetailsFails() {
        MerchantSemanticSearchResult merchant = merchant("shoe.example", "Shoe Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));
        merchantCatalogPluginDispatchService.failures.put("runner", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(result.products()).hasSize(1);
        assertThat(result.products().getFirst().productId()).isEqualTo("runner");
        assertThat(result.products().getFirst().detailError()).isEqualTo("Product details unavailable");
        assertThat(result.products().getFirst().selectedVariantId()).isEqualTo("runner-variant");
        assertThat(result.products().getFirst().selectedVariantPriceAmount()).isEqualTo("10.00");
    }

    @Test
    void streamsCatalogCandidateBeforeAsyncProductDetailsUpdate() {
        MerchantSemanticSearchResult merchant = merchant("shoe.example", "Shoe Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));
        List<MerchantSemanticProductResult> streamedProducts = new CopyOnWriteArrayList<>();

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null),
                streamedProducts::add
        );

        assertThat(result.products()).singleElement()
                .satisfies(product -> assertThat(product.selectedVariantId()).isEqualTo("runner-selected"));
        assertThat(streamedProducts).hasSize(2);
        assertThat(streamedProducts.getFirst()).satisfies(product -> {
            assertThat(product.productId()).isEqualTo("runner");
            assertThat(product.selectedVariantId()).isEqualTo("runner-variant");
            assertThat(product.detailDescription()).isNull();
        });
        assertThat(streamedProducts.getLast()).satisfies(product -> {
            assertThat(product.productId()).isEqualTo("runner");
            assertThat(product.selectedVariantId()).isEqualTo("runner-selected");
            assertThat(product.detailDescription()).isEqualTo("Detailed description");
        });
    }

    @Test
    void capturesRichCatalogDataWhenProductDetailsAreMissing() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct())));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.listPriceAmount()).isEqualTo(5200L);
            assertThat(product.listPriceCurrency()).isEqualTo("USD");
            assertThat(product.ratingScore()).isEqualTo(4.8d);
            assertThat(product.reviewCount()).isEqualTo(214);
            assertThat(product.media()).extracting("type").containsExactly("image", "video");
            assertThat(product.categories()).extracting("value").containsExactly("Apparel");
            assertThat(product.certifications()).containsExactly("GOTS");
            assertThat(product.materials()).containsExactly("Organic cotton", "100% organic cotton");
            assertThat(product.skus()).containsExactly("SKU-RICH", "SKU-RICH-VARIANT");
            assertThat(product.collections()).containsExactly("Basics");
            assertThat(product.attributes()).extracting("name").contains("fabric", "technical specification");
        });
    }

    @Test
    void parsesLocalizedDecimalRatingValues() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(
                new CatalogSearchResponse.Money(5200L, "USD"),
                new CatalogRating(4.75d, 5.0d, 1234567),
                1234567,
                JSON.valueToTree(Map.of("fabric", "100% organic cotton")),
                List.of("relaxed")
        ))));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.ratingScore()).isEqualTo(4.75d);
            assertThat(product.reviewCount()).isEqualTo(1234567);
        });
    }

    @Test
    void treatsSingleDotRatingDecimalAsDecimalValue() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(
                new CatalogSearchResponse.Money(5200L, "USD"),
                new CatalogRating(4.5d, 5.0d, null),
                214,
                JSON.valueToTree(Map.of("fabric", "100% organic cotton")),
                List.of("relaxed")
        ))));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.ratingScore()).isEqualTo(4.5d);
            assertThat(product.reviewCount()).isEqualTo(214);
        });
    }

    @Test
    void ignoresNullRichCatalogListItems() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                richProductWithNullCatalogItems()
        )));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.listPriceAmount()).isEqualTo(5200L);
            assertThat(product.media()).extracting("type").containsExactly("image", "video");
            assertThat(product.categories()).extracting("value").containsExactly("Apparel");
            assertThat(product.skus()).containsExactly("SKU-RICH", "SKU-RICH-VARIANT");
        });
    }

    @Test
    void filtersNullProductDetailListItems() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("detail-tee", "Detail Tee", "Soft tee", "apparel")
        )));
        merchantCatalogPluginDispatchService.products.put("detail-tee", detailProductWithNullListItems("detail-tee"));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("soft tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.detailImages()).extracting("url")
                    .containsExactly("https://example.com/detail-tee-detail.jpg");
            assertThat(product.detailOptions()).extracting("name").containsExactly("Size");
            assertThat(product.sellingPlanGroups())
                    .extracting(ProductSellingPlanGroup::name)
                    .containsExactly("Subscribe");
            assertThat(product.selectedOptions()).extracting("name").containsExactly("Size");
            MerchantSemanticProductResponse response = MerchantSemanticProductResponse.from(product);
            assertThat(response.detailImages()).hasSize(1);
            assertThat(response.detailOptions()).hasSize(1);
            assertThat(response.selectedOptions()).hasSize(1);
            assertThat(response.sellingPlanGroups()).singleElement().satisfies(group -> {
                assertThat(group.id()).isEqualTo("subscription-group");
                assertThat(group.appName()).isEqualTo("Subscriptions");
                assertThat(group.options()).singleElement()
                        .satisfies(option -> assertThat(option.values()).containsExactly("Monthly"));
                assertThat(group.sellingPlans()).singleElement().satisfies(plan -> {
                    assertThat(plan.id()).isEqualTo("monthly-plan");
                    assertThat(plan.options()).singleElement()
                            .satisfies(option -> assertThat(option.value()).isEqualTo("Monthly"));
                });
            });
        });
    }

    @Test
    void handlesDeeplyNestedCatalogMetadata() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(
                new CatalogSearchResponse.Money(5200L, "USD"),
                new CatalogRating(4.8d, 5.0d, 214),
                214,
                deeplyNestedValue(10_000),
                null
        ))));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement()
                .satisfies(product -> assertThat(product.productId()).isEqualTo("rich-tee"));
    }

    @Test
    void treatsTypedIntegerListPricesAsMinorUnits() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(
                merchant.domain(), catalogSearchResult(merchant, List.of(
                        richProduct(new CatalogSearchResponse.Money(1200L, "USD")))));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.listPriceAmount()).isEqualTo(1200L);
            assertThat(product.listPriceCurrency()).isEqualTo("USD");
        });
    }

    @Test
    void preservesExplicitMinorUnitListPrices() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                richProduct(new CatalogSearchResponse.Money(5200L, "USD"))
        )));
        merchantCatalogPluginDispatchService.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.listPriceAmount()).isEqualTo(5200L);
            assertThat(product.listPriceCurrency()).isEqualTo("USD");
        });
    }

    @Test
    void searchesOnlyRequestedMerchantWhenMerchantIdIsProvided() {
        UUID merchantId = UUID.randomUUID();
        merchantLookupService.results.put(merchantId, new MerchantSemanticSearchResult(
                merchantId,
                "focused.example",
                "Focused Store",
                "https://focused.example/api/mcp",
                null,
                "Focused catalog",
                1.0d,
                1.0d,
                1
        ));
        merchantSemanticSearchService.results = List.of(merchant("other.example", "Other Store", 1));
        merchantCatalogPluginDispatchService.results.put("focused.example", new CatalogSearchResult(
                "https://focused.example/api/mcp",
                List.of(product("focused-runner", "Focused Running Shoe", "Light road shoe", "shoes"))
        ));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", merchantId, null, null, null, null)
        );

        assertThat(merchantSemanticSearchService.lastQuery).isNull();
        assertThat(merchantCatalogPluginDispatchService.catalogSearchCalls).containsExactly("focused.example:running shoes:2");
        assertThat(result.merchants()).extracting("domain").containsExactly("focused.example");
        assertThat(result.products()).extracting("productId").containsExactly("focused-runner");
    }

    @Test
    void searchesMerchantCatalogsInParallel() {
        MerchantSemanticSearchResult firstMerchant = merchant("first.example", "First Store", 1);
        MerchantSemanticSearchResult secondMerchant = merchant("second.example", "Second Store", 2);
        merchantSemanticSearchService.results = List.of(firstMerchant, secondMerchant);
        merchantCatalogPluginDispatchService.concurrentCatalogSearches = new CountDownLatch(2);
        merchantCatalogPluginDispatchService.results.put(firstMerchant.domain(), catalogSearchResult(firstMerchant, List.of(
                product("first-lamp", "First Lamp", "Warm lamp", "lighting")
        )));
        merchantCatalogPluginDispatchService.results.put(secondMerchant.domain(), catalogSearchResult(secondMerchant, List.of(
                product("second-lamp", "Second Lamp", "Warm lamp", "lighting")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("warm lamp", null, null, null, null, null)
        );

        assertThat(result.merchants()).extracting("domain")
                .containsExactly("first.example", "second.example");
        assertThat(merchantCatalogPluginDispatchService.catalogSearchCalls).containsExactlyInAnyOrder(
                "first.example:warm lamp:2",
                "second.example:warm lamp:2"
        );
    }

    @Test
    void fetchesProductDetailsInParallel() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.concurrentGetProducts = new CountDownLatch(2);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("first-lamp", "First Lamp", "Warm lamp", "lighting"),
                product("second-lamp", "Second Lamp", "Warm lamp", "lighting")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("warm lamp", null, null, null, null, null)
        );

        assertThat(result.products()).hasSize(2);
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactlyInAnyOrder(
                "home.example:first-lamp",
                "home.example:second-lamp"
        );
    }

    @Test
    void appliesPriceFilterBeforeRerankingAndDetailsWhenMerchantReturnsUnfilteredProducts() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("cheap-pillow", "Affordable Throw Pillow", "Soft pillow", "pillows", 8000L),
                product("expensive-pillow", "Premium Throw Pillow", "Soft pillow", "pillows", 15000L)
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "throw pillow",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CatalogSearchContext(
                                "US",
                                null,
                                null,
                                "en",
                                "USD",
                                "Original request: throw pillow under 100 USD"
                        ),
                        null,
                        new CatalogSearchFilters(List.of(), new CatalogSearchPriceFilter(null, 10000L))
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("cheap-pillow");
        assertThat(voyageRerankClient.documents).hasSize(1);
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactly("home.example:cheap-pillow");
    }

    @Test
    void keepsProductWhenOnlyCatalogMaxPriceAndNoCurrencyAreKnown() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("known-max-pillow", "Affordable Throw Pillow", "Soft pillow", "pillows", null, 8000L, null)
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "throw pillow",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CatalogSearchContext("US", null, null, "en", "USD", "Original request: pillow under 100 USD"),
                        null,
                        new CatalogSearchFilters(List.of(), new CatalogSearchPriceFilter(null, 10000L))
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("known-max-pillow");
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactly("home.example:known-max-pillow");
    }

    @Test
    void appliesDetailPriceFilterWithGroupedEuropeanDecimalSeparators() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("euro-lamp", "European Lamp", "Warm lamp", "lighting", 123456L, 123456L, "EUR")
        )));
        merchantCatalogPluginDispatchService.prices.put("euro-lamp", "1.234,56");
        merchantCatalogPluginDispatchService.currencies.put("euro-lamp", "EUR");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "lamp over 1000 EUR",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CatalogSearchContext("DE", null, null, "en", "EUR", "Original request: lamp over 1000 EUR"),
                        null,
                        new CatalogSearchFilters(List.of(), new CatalogSearchPriceFilter(100000L, null))
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("euro-lamp");
    }

    @Test
    void appliesDetailPriceFilterWithSingleDotDecimalSeparator() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("decimal-candle", "Decimal Candle", "Small candle", "decor", 450L, 450L, "USD")
        )));
        merchantCatalogPluginDispatchService.prices.put("decimal-candle", "4.500");
        merchantCatalogPluginDispatchService.currencies.put("decimal-candle", "USD");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "candle under 5 USD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CatalogSearchContext("US", null, null, "en", "USD", "Original request: candle under 5 USD"),
                        null,
                        new CatalogSearchFilters(List.of(), new CatalogSearchPriceFilter(null, 500L))
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("decimal-candle");
    }

    @Test
    void appliesMensFitAudienceFilterBeforeRerankingWhenCatalogAudienceIsExplicit() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("women-swimsuit", "Women's Swimsuit", "One-piece swimwear", "Women's Swimwear"),
                product("men-swim-shorts", "Men's Swim Shorts", "Quick-dry swim shorts", "Men's Swimwear")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "swimming shorts",
                        null,
                        null,
                        null,
                        null,
                        null,
                        mensFitContext("swimming shorts"),
                        null,
                        null
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("men-swim-shorts");
        assertThat(voyageRerankClient.documents).hasSize(1);
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactly("apparel.example:men-swim-shorts");
    }

    @Test
    void appliesMensFitAudienceFilterAfterDetailsRevealWomenProductType() {
        MerchantSemanticSearchResult merchant = merchant("billabong.com", "Billabong", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("palm-viva", "Palm Viva Woven Shorts - Sweet Lilac", "Open-weave cotton shorts", "Clothing"),
                product("mens-boardshort", "Sundown Boardshorts", "Swim shorts with a drawcord", "Clothing")
        )));
        merchantCatalogPluginDispatchService.products.put("palm-viva", detailProduct(
                "palm-viva",
                "Palm Viva Woven Shorts - Sweet Lilac",
                "Product Type: Women's casual woven shorts for everyday wear and swim cover-up"
        ));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "swimming shorts",
                        null,
                        null,
                        null,
                        null,
                        null,
                        mensFitContext("swimming shorts"),
                        null,
                        null
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("mens-boardshort");
        assertThat(merchantCatalogPluginDispatchService.getProductCalls).containsExactlyInAnyOrder(
                "billabong.com:palm-viva",
                "billabong.com:mens-boardshort"
        );
    }

    @Test
    void appliesWomensFitAudienceFilterAfterDetailsRevealMenProductType() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogPluginDispatchService.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("mens-boardshort", "Sundown Boardshorts", "Swim shorts with a drawcord", "Clothing"),
                product("women-swim-short", "High-Rise Swim Short", "Swim shorts with a relaxed fit", "Clothing")
        )));
        merchantCatalogPluginDispatchService.products.put("mens-boardshort", detailProduct(
                "mens-boardshort",
                "Sundown Boardshorts",
                "Product Type: Men's swim trunks"
        ));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        "swimming shorts",
                        null,
                        null,
                        null,
                        null,
                        null,
                        womensFitContext("swimming shorts"),
                        null,
                        null
                )
        );

        assertThat(result.products()).extracting("productId").containsExactly("women-swim-short");
    }

    private MerchantSemanticSearchResult merchant(String domain, String name, int rank) {
        return new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                domain,
                name,
                "https://" + domain + "/api/mcp",
                null,
                "Categories: Shoes",
                0.8d - rank * 0.01d,
                0.9d - rank * 0.01d,
                rank
        );
    }

    private CatalogSearchContext mensFitContext(String query) {
        return new CatalogSearchContext(
                "US",
                null,
                null,
                "en",
                "USD",
                "Original request: %s; Catalog query: %s; Hard apparel audience filter: men's sizing"
                        .formatted(query, query)
        );
    }

    private CatalogSearchContext womensFitContext(String query) {
        return new CatalogSearchContext(
                "US",
                null,
                null,
                "en",
                "USD",
                "Original request: %s; Catalog query: %s; Hard apparel audience filter: women's sizing"
                        .formatted(query, query)
        );
    }

    private CatalogSearchResult catalogSearchResult(
            MerchantSemanticSearchResult merchant,
            List<CatalogSearchResponse.Product> products
    ) {
        return new CatalogSearchResult(merchant.advertisedMcpEndpoint(), products);
    }

    private CatalogSearchResponse.Product richProduct() {
        return richProduct(new CatalogSearchResponse.Money(5200L, "USD"));
    }

    private CatalogSearchResponse.Product richProduct(CatalogSearchResponse.Money listPrice) {
        return richProduct(
                listPrice,
                new CatalogRating(4.8d, 5.0d, 214),
                214,
                JSON.valueToTree(Map.of("fabric", "100% organic cotton")),
                List.of("relaxed")
        );
    }

    private CatalogSearchResponse.Product richProduct(
            CatalogSearchResponse.Money listPrice,
            CatalogRating rating,
            Integer reviewCount,
            JsonNode metadata,
            List<String> techSpecs
    ) {
        return new CatalogSearchResponse.Product(
                "rich-tee",
                "Organic Cotton Tee",
                new CatalogSearchResponse.Description("<p>Organic cotton tee.</p>"),
                "https://example.com/products/rich-tee",
                new CatalogSearchResponse.PriceRange(
                        new CatalogSearchResponse.Money(3800L, "USD"),
                        new CatalogSearchResponse.Money(3800L, "USD")
                ),
                listPrice,
                rating,
                reviewCount,
                List.of(new CatalogSearchResponse.Variant(
                        "rich-tee-variant",
                        "Default Title",
                        new CatalogSearchResponse.Description("<p>Organic cotton tee.</p>"),
                        new CatalogSearchResponse.Money(3800L, "USD"),
                        "SKU-RICH-VARIANT",
                        listPrice,
                        new CatalogSearchResponse.Availability(true),
                        List.of(new CatalogSearchResponse.Media(
                                "video",
                                "https://example.com/rich-tee.mp4",
                                "Fit video",
                                null
                        ))
                )),
                List.of(new CatalogSearchResponse.Media(
                        "image",
                        "https://example.com/rich-tee.jpg",
                        "Organic cotton tee",
                        null
                )),
                List.of(new CatalogSearchResponse.Category("Apparel", "shopify")),
                List.of("organic"),
                List.of("SKU-RICH"),
                List.of("GOTS"),
                List.of("Organic cotton"),
                List.of("Basics"),
                metadata,
                null,
                techSpecs
        );
    }

    private ProductDetailsResponse.Product detailProductWithNullListItems(String productId) {
        List<ProductDetailsResponse.Image> images = new ArrayList<>();
        images.add(null);
        images.add(new ProductDetailsResponse.Image(
                "https://example.com/" + productId + "-detail.jpg",
                "Product image"
        ));
        List<ProductDetailsResponse.Option> options = new ArrayList<>();
        options.add(null);
        options.add(new ProductDetailsResponse.Option("Size", List.of("Default")));
        List<ProductDetailsResponse.SellingPlanGroup> sellingPlanGroups = new ArrayList<>();
        sellingPlanGroups.add(null);
        sellingPlanGroups.add(new ProductDetailsResponse.SellingPlanGroup(
                "subscription-group",
                "Subscribe",
                "Subscriptions",
                List.of(new ProductDetailsResponse.SellingPlanGroup.GroupOption(
                        "Delivery", List.of("Monthly"))),
                List.of(new ProductDetailsResponse.SellingPlanGroup.SellingPlan(
                        "monthly-plan",
                        "Monthly subscription",
                        "Delivered monthly",
                        List.of(new ProductDetailsResponse.SellingPlanGroup.SellingPlanOption(
                                "Delivery", "Monthly"))))));
        List<ProductDetailsResponse.SelectedOption> selectedOptions = new ArrayList<>();
        selectedOptions.add(null);
        selectedOptions.add(new ProductDetailsResponse.SelectedOption("Size", "Default"));
        return new ProductDetailsResponse.Product(
                productId,
                productId + " detail",
                "Detailed description",
                "https://example.com/products/" + productId,
                "https://example.com/" + productId + "-detail.jpg",
                images,
                options,
                1,
                new ProductDetailsResponse.PriceRange("12.95", "12.95", "USD"),
                false,
                sellingPlanGroups,
                new ProductDetailsResponse.SelectedVariant(
                        productId + "-selected",
                        "Default",
                        "12.95",
                        "USD",
                        "https://example.com/" + productId + "-variant.jpg",
                        "Variant image",
                        true,
                        selectedOptions
                )
        );
    }

    private CatalogSearchResponse.Product richProductWithNullCatalogItems() {
        List<CatalogSearchResponse.Media> variantMedia = new ArrayList<>();
        variantMedia.add(null);
        variantMedia.add(new CatalogSearchResponse.Media(
                "video",
                "https://example.com/rich-tee.mp4",
                "Fit video",
                null
        ));
        List<CatalogSearchResponse.Variant> variants = new ArrayList<>();
        variants.add(null);
        variants.add(new CatalogSearchResponse.Variant(
                "rich-tee-variant",
                "Default Title",
                new CatalogSearchResponse.Description("<p>Organic cotton tee.</p>"),
                new CatalogSearchResponse.Money(3800L, "USD"),
                "SKU-RICH-VARIANT",
                new CatalogSearchResponse.Money(5200L, "USD"),
                new CatalogSearchResponse.Availability(true),
                variantMedia
        ));
        List<CatalogSearchResponse.Media> media = new ArrayList<>();
        media.add(null);
        media.add(new CatalogSearchResponse.Media(
                "image",
                "https://example.com/rich-tee.jpg",
                "Organic cotton tee",
                null
        ));
        List<CatalogSearchResponse.Category> categories = new ArrayList<>();
        categories.add(null);
        categories.add(new CatalogSearchResponse.Category("Apparel", "shopify"));
        return new CatalogSearchResponse.Product(
                "rich-tee",
                "Organic Cotton Tee",
                new CatalogSearchResponse.Description("<p>Organic cotton tee.</p>"),
                "https://example.com/products/rich-tee",
                new CatalogSearchResponse.PriceRange(
                        new CatalogSearchResponse.Money(3800L, "USD"),
                        new CatalogSearchResponse.Money(3800L, "USD")
                ),
                null,
                new CatalogRating(4.8d, 5.0d, 214),
                214,
                variants,
                media,
                categories,
                List.of("organic"),
                List.of("SKU-RICH"),
                List.of("GOTS"),
                List.of("Organic cotton"),
                List.of("Basics"),
                JSON.valueToTree(Map.of("fabric", "100% organic cotton")),
                null,
                List.of("relaxed")
        );
    }

    private ProductDetailsResponse.Product detailProduct(String productId, String title, String description) {
        return new ProductDetailsResponse.Product(
                productId,
                title,
                description,
                "https://example.com/products/" + productId,
                "https://example.com/" + productId + "-detail.jpg",
                List.of(new ProductDetailsResponse.Image(
                        "https://example.com/" + productId + "-detail.jpg",
                        title
                )),
                List.of(new ProductDetailsResponse.Option("Size", List.of("Default"))),
                1,
                new ProductDetailsResponse.PriceRange("12.95", "12.95", "USD"),
                false,
                List.of(),
                new ProductDetailsResponse.SelectedVariant(
                        productId + "-selected",
                        "Default",
                        "12.95",
                        "USD",
                        "https://example.com/" + productId + "-variant.jpg",
                        title,
                        true,
                        List.of(new ProductDetailsResponse.SelectedOption("Size", "Default"))
                )
        );
    }

    private JsonNode deeplyNestedValue(int depth) {
        JsonNode value = JSON.valueToTree("GOTS");
        for (int index = 0; index < depth; index++) {
            value = JSON.createArrayNode().add(value);
        }
        return value;
    }

    private CatalogSearchResponse.Product product(String id, String title, String description, String category) {
        return product(id, title, description, category, 1000L);
    }

    private CatalogSearchResponse.Product product(
            String id,
            String title,
            String description,
            String category,
            Long price
    ) {
        return product(id, title, description, category, price, price, "USD");
    }

    private CatalogSearchResponse.Product product(
            String id,
            String title,
            String description,
            String category,
            Long minPrice,
            Long maxPrice,
            String currency
    ) {
        return new CatalogSearchResponse.Product(
                id,
                title,
                new CatalogSearchResponse.Description("<p>" + description + "</p>"),
                "https://example.com/products/" + id,
                new CatalogSearchResponse.PriceRange(
                        new CatalogSearchResponse.Money(minPrice, currency),
                        new CatalogSearchResponse.Money(maxPrice, currency)
                ),
                List.of(new CatalogSearchResponse.Variant(
                        id + "-variant",
                        "Default Title",
                        new CatalogSearchResponse.Description("<p>" + description + "</p>"),
                        new CatalogSearchResponse.Money(minPrice == null ? maxPrice : minPrice, currency),
                        new CatalogSearchResponse.Availability(true),
                        List.of(new CatalogSearchResponse.Media("image", "https://example.com/" + id + ".jpg"))
                )),
                List.of(new CatalogSearchResponse.Media("image", "https://example.com/" + id + ".jpg")),
                List.of(new CatalogSearchResponse.Category(category, "shopify")),
                List.of(category)
        );
    }

    private static void awaitConcurrentCalls(CountDownLatch latch, String operation) {
        if (latch == null) {
            return;
        }
        latch.countDown();
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError(operation + " calls did not run in parallel");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(operation + " calls were interrupted", exception);
        }
    }

    static class FakeMerchantSemanticSearchService extends MerchantSemanticSearchService {

        private List<MerchantSemanticSearchResult> results = List.of();
        private SemanticMerchantSearchQuery lastQuery;

        FakeMerchantSemanticSearchService() {
            super(null, null, null, null);
        }

        @Override
        public List<MerchantSemanticSearchResult> search(SemanticMerchantSearchQuery query) {
            lastQuery = query;
            return results;
        }
    }

    static class FakeMerchantLookupService extends MerchantLookupService {

        private final Map<UUID, MerchantSemanticSearchResult> results = new HashMap<>();

        FakeMerchantLookupService() {
            super(null, null);
        }

        @Override
        public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
            return results.get(merchantId);
        }
    }

    static class FakeMerchantCatalogPluginDispatchService extends MerchantCatalogPluginDispatchService {

        private final Map<String, CatalogSearchResult> results = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private final Map<String, String> runtimeFailures = new HashMap<>();
        private final Map<String, ProductDetailsResponse.Product> products = new HashMap<>();
        private final Map<String, String> prices = new HashMap<>();
        private final Map<String, String> currencies = new HashMap<>();
        private final List<String> catalogSearchCalls = new CopyOnWriteArrayList<>();
        private final List<String> lookupCalls = new CopyOnWriteArrayList<>();
        private final List<String> getProductCalls = new CopyOnWriteArrayList<>();
        private CountDownLatch concurrentCatalogSearches;
        private CountDownLatch concurrentGetProducts;

        FakeMerchantCatalogPluginDispatchService() {
            super(null, null);
        }

        @Override
        public CatalogSearchResult searchCatalog(
                MerchantSemanticSearchResult merchant,
                String query,
                CatalogSearchContext context,
                CatalogSearchSignals signals,
                CatalogSearchFilters filters,
                int limit
        ) {
            catalogSearchCalls.add(merchant.domain() + ":" + query + ":" + limit);
            awaitConcurrentCalls(concurrentCatalogSearches, "Catalog search");
            if (runtimeFailures.containsKey(merchant.domain())) {
                throw new IllegalStateException(runtimeFailures.get(merchant.domain()));
            }
            if (failures.containsKey(merchant.domain())) {
                throw new MerchantCatalogSearchException(failures.get(merchant.domain()));
            }
            return results.get(merchant.domain());
        }

        @Override
        public CatalogLookupResult lookupCatalog(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context,
                com.meant.api.plugin.spi.NegotiatedCapabilities activeCapabilities
        ) {
            lookupCalls.add(merchant.domain() + ":" + productId);
            if (failures.containsKey(productId)) {
                throw new MerchantProductDetailsException(failures.get(productId));
            }
            return new CatalogLookupResult(merchant.advertisedMcpEndpoint(), productId, products.get(productId), null);
        }

        @Override
        public ProductDetailsResult getProduct(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context,
                com.meant.api.plugin.spi.NegotiatedCapabilities activeCapabilities
        ) {
            getProductCalls.add(merchant.domain() + ":" + productId);
            awaitConcurrentCalls(concurrentGetProducts, "get_product");
            if (failures.containsKey(productId)) {
                throw new MerchantProductDetailsException(failures.get(productId));
            }
            ProductDetailsResponse.Product product = products.get(productId);
            if (product != null) {
                return new ProductDetailsResult(merchant.advertisedMcpEndpoint(), "{}", product);
            }
            String price = prices.getOrDefault(productId, "12.95");
            String currency = currencies.getOrDefault(productId, "USD");
            return new ProductDetailsResult(
                    merchant.advertisedMcpEndpoint(),
                    "{}",
                    new ProductDetailsResponse.Product(
                            productId,
                            productId + " detail",
                            "Detailed description",
                            "https://example.com/products/" + productId,
                            "https://example.com/" + productId + "-detail.jpg",
                            List.of(new ProductDetailsResponse.Image(
                                    "https://example.com/" + productId + "-detail.jpg",
                                    "Product image"
                            )),
                            List.of(new ProductDetailsResponse.Option("Size", List.of("Default"))),
                            1,
                            new ProductDetailsResponse.PriceRange(price, price, currency),
                            false,
                            List.of(),
                            new ProductDetailsResponse.SelectedVariant(
                                    productId + "-selected",
                                    "Default",
                                    price,
                                    currency,
                                    "https://example.com/" + productId + "-variant.jpg",
                                    "Variant image",
                                    true,
                                    List.of(new ProductDetailsResponse.SelectedOption("Size", "Default"))
                            )
                    )
            );
        }
    }

    static class FakeVoyageRerankClient extends VoyageRerankClient {

        private List<String> documents = List.of();

        FakeVoyageRerankClient() {
            super(RestClient.builder(), null);
        }

        @Override
        public List<VoyageRerankResult> rerank(String query, List<String> documents) {
            this.documents = documents;
            List<VoyageRerankResult> results = new ArrayList<>();
            for (int index = 0; index < documents.size(); index++) {
                results.add(new VoyageRerankResult(index, relevanceScore(documents.get(index))));
            }
            return results;
        }

        private double relevanceScore(String document) {
            String normalizedDocument = document.toLowerCase(Locale.ROOT);
            if (normalizedDocument.contains("trail running shoe")) {
                return 0.95d;
            }
            if (normalizedDocument.contains("casual sneaker")) {
                return 0.75d;
            }
            if (normalizedDocument.contains("running shoe")) {
                return 0.7d;
            }
            if (normalizedDocument.contains("shoe")) {
                return 0.4d;
            }
            return 0.1d;
        }
    }
}
