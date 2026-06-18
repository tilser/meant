package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
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
import org.springframework.web.client.RestClient;

class MerchantSemanticProductSearchServiceTest {

    private FakeMerchantSemanticSearchService merchantSemanticSearchService;
    private FakeMerchantCatalogSearchClient merchantCatalogSearchClient;
    private FakeMerchantProductDetailsClient merchantProductDetailsClient;
    private FakeVoyageRerankClient voyageRerankClient;
    private FakeMerchantLookupService merchantLookupService;
    private MerchantSemanticProductSearchService merchantSemanticProductSearchService;

    @BeforeEach
    void setUp() {
        merchantSemanticSearchService = new FakeMerchantSemanticSearchService();
        merchantCatalogSearchClient = new FakeMerchantCatalogSearchClient();
        merchantProductDetailsClient = new FakeMerchantProductDetailsClient();
        voyageRerankClient = new FakeVoyageRerankClient();
        merchantLookupService = new FakeMerchantLookupService();
        merchantSemanticProductSearchService = new MerchantSemanticProductSearchService(
                merchantSemanticSearchService,
                merchantCatalogSearchClient,
                merchantProductDetailsClient,
                voyageRerankClient,
                merchantLookupService,
                new MerchantCatalogSearchProperties(3, 2, 2, 2)
        );
    }

    @Test
    void searchesTopRerankedMerchantsAndReranksProductsAcrossCatalogs() {
        MerchantSemanticSearchResult homeMerchant = merchant("home.example", "Home Store", 1);
        MerchantSemanticSearchResult shoeMerchant = merchant("shoe.example", "Shoe Store", 2);
        MerchantSemanticSearchResult skippedMerchant = merchant("skipped.example", "Skipped Store", 3);
        merchantSemanticSearchService.results = List.of(homeMerchant, shoeMerchant, skippedMerchant);
        merchantCatalogSearchClient.results.put(homeMerchant.domain(), catalogSearchResult(homeMerchant, List.of(
                product("home-lamp", "Modern Table Lamp", "A warm lamp for desks", "home-lighting"),
                product("home-sneaker-rack", "Entryway Shoe Rack", "Storage for running shoes", "home-storage")
        )));
        merchantCatalogSearchClient.results.put(shoeMerchant.domain(), catalogSearchResult(shoeMerchant, List.of(
                product("trail-runner", "Trail Running Shoe", "Grip for long runs", "shoes"),
                product("casual-sneaker", "Casual Sneaker", "Everyday walking shoe", "shoes")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(merchantSemanticSearchService.lastQuery.limit()).isEqualTo(3);
        assertThat(merchantCatalogSearchClient.calls).containsExactlyInAnyOrder(
                "home.example:running shoes:2",
                "shoe.example:running shoes:2"
        );
        assertThat(voyageRerankClient.documents).hasSize(4);
        assertThat(result.merchants()).extracting("domain")
                .containsExactly("home.example", "shoe.example");
        assertThat(result.products()).extracting("productId")
                .containsExactly("trail-runner", "casual-sneaker");
        assertThat(merchantProductDetailsClient.calls).containsExactlyInAnyOrder(
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
        merchantCatalogSearchClient.failures.put(failingMerchant.domain(), "catalog unavailable");
        merchantCatalogSearchClient.results.put(workingMerchant.domain(), catalogSearchResult(workingMerchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(result.merchants()).hasSize(2);
        assertThat(result.merchants().getFirst().error()).contains("catalog unavailable");
        assertThat(result.merchants().getLast().productCount()).isEqualTo(1);
        assertThat(result.products()).extracting("productId").containsExactly("runner");
    }

    @Test
    void keepsPartialProductWhenProductDetailsFails() {
        MerchantSemanticSearchResult merchant = merchant("shoe.example", "Shoe Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("runner", "Running Shoe", "Light road shoe", "shoes")
        )));
        merchantProductDetailsClient.failures.put("runner", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", null, null, null, null, null)
        );

        assertThat(result.products()).hasSize(1);
        assertThat(result.products().getFirst().productId()).isEqualTo("runner");
        assertThat(result.products().getFirst().detailError()).contains("details unavailable");
        assertThat(result.products().getFirst().selectedVariantId()).isNull();
    }

    @Test
    void capturesRichCatalogDataWhenProductDetailsAreMissing() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct())));
        merchantProductDetailsClient.failures.put("rich-tee", "details unavailable");

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
            assertThat(product.attributes()).extracting("name").contains("fabric", "fit");
        });
    }

    @Test
    void parsesLocalizedDecimalRatingValues() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(
                new CatalogSearchResponse.Money(5200L, "USD"),
                Map.of("value", "4,75", "reviewCount", "1,234"),
                "1,234",
                Map.of("fabric", "100% organic cotton"),
                Map.of("fit", "relaxed")
        ))));
        merchantProductDetailsClient.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.ratingScore()).isEqualTo(4.75d);
            assertThat(product.reviewCount()).isEqualTo(1234);
        });
    }

    @Test
    void handlesDeeplyNestedCatalogMetadata() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(
                new CatalogSearchResponse.Money(5200L, "USD"),
                Map.of("value", 4.8d, "reviewCount", 214),
                214,
                deeplyNestedValue(10_000),
                null
        ))));
        merchantProductDetailsClient.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement()
                .satisfies(product -> assertThat(product.productId()).isEqualTo("rich-tee"));
    }

    @Test
    void treatsRawNumericListPricesAsMajorUnits() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(richProduct(1200))));
        merchantProductDetailsClient.failures.put("rich-tee", "details unavailable");

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("organic cotton tee", null, null, null, null, null)
        );

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.listPriceAmount()).isEqualTo(120000L);
            assertThat(product.listPriceCurrency()).isEqualTo("USD");
        });
    }

    @Test
    void preservesExplicitMinorUnitListPrices() {
        MerchantSemanticSearchResult merchant = merchant("apparel.example", "Apparel Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                richProduct(Map.of("amount_cents", 5200, "currency", "USD"))
        )));
        merchantProductDetailsClient.failures.put("rich-tee", "details unavailable");

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
        merchantCatalogSearchClient.results.put("focused.example", new CatalogSearchResult(
                "https://focused.example/api/mcp",
                List.of(product("focused-runner", "Focused Running Shoe", "Light road shoe", "shoes"))
        ));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("running shoes", merchantId, null, null, null, null)
        );

        assertThat(merchantSemanticSearchService.lastQuery).isNull();
        assertThat(merchantCatalogSearchClient.calls).containsExactly("focused.example:running shoes:2");
        assertThat(result.merchants()).extracting("domain").containsExactly("focused.example");
        assertThat(result.products()).extracting("productId").containsExactly("focused-runner");
    }

    @Test
    void searchesMerchantCatalogsInParallel() {
        MerchantSemanticSearchResult firstMerchant = merchant("first.example", "First Store", 1);
        MerchantSemanticSearchResult secondMerchant = merchant("second.example", "Second Store", 2);
        merchantSemanticSearchService.results = List.of(firstMerchant, secondMerchant);
        merchantCatalogSearchClient.concurrentCatalogSearches = new CountDownLatch(2);
        merchantCatalogSearchClient.results.put(firstMerchant.domain(), catalogSearchResult(firstMerchant, List.of(
                product("first-lamp", "First Lamp", "Warm lamp", "lighting")
        )));
        merchantCatalogSearchClient.results.put(secondMerchant.domain(), catalogSearchResult(secondMerchant, List.of(
                product("second-lamp", "Second Lamp", "Warm lamp", "lighting")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("warm lamp", null, null, null, null, null)
        );

        assertThat(result.merchants()).extracting("domain")
                .containsExactly("first.example", "second.example");
        assertThat(merchantCatalogSearchClient.calls).containsExactlyInAnyOrder(
                "first.example:warm lamp:2",
                "second.example:warm lamp:2"
        );
    }

    @Test
    void fetchesProductDetailsInParallel() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantProductDetailsClient.concurrentProductDetails = new CountDownLatch(2);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("first-lamp", "First Lamp", "Warm lamp", "lighting"),
                product("second-lamp", "Second Lamp", "Warm lamp", "lighting")
        )));

        MerchantSemanticProductSearchResult result = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery("warm lamp", null, null, null, null, null)
        );

        assertThat(result.products()).hasSize(2);
        assertThat(merchantProductDetailsClient.calls).containsExactlyInAnyOrder(
                "home.example:first-lamp",
                "home.example:second-lamp"
        );
    }

    @Test
    void appliesPriceFilterBeforeRerankingAndDetailsWhenMerchantReturnsUnfilteredProducts() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
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
        assertThat(merchantProductDetailsClient.calls).containsExactly("home.example:cheap-pillow");
    }

    @Test
    void keepsProductWhenOnlyCatalogMaxPriceAndNoCurrencyAreKnown() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
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
        assertThat(merchantProductDetailsClient.calls).containsExactly("home.example:known-max-pillow");
    }

    @Test
    void appliesDetailPriceFilterWithGroupedEuropeanDecimalSeparators() {
        MerchantSemanticSearchResult merchant = merchant("home.example", "Home Store", 1);
        merchantSemanticSearchService.results = List.of(merchant);
        merchantCatalogSearchClient.results.put(merchant.domain(), catalogSearchResult(merchant, List.of(
                product("euro-lamp", "European Lamp", "Warm lamp", "lighting", 123456L, 123456L, "EUR")
        )));
        merchantProductDetailsClient.prices.put("euro-lamp", "1.234,56");
        merchantProductDetailsClient.currencies.put("euro-lamp", "EUR");

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

    private CatalogSearchResult catalogSearchResult(
            MerchantSemanticSearchResult merchant,
            List<CatalogSearchResponse.Product> products
    ) {
        return new CatalogSearchResult(merchant.advertisedMcpEndpoint(), products);
    }

    private CatalogSearchResponse.Product richProduct() {
        return richProduct(new CatalogSearchResponse.Money(5200L, "USD"));
    }

    private CatalogSearchResponse.Product richProduct(Object listPrice) {
        return richProduct(
                listPrice,
                Map.of("value", 4.8d, "reviewCount", 214),
                214,
                Map.of("fabric", "100% organic cotton"),
                Map.of("fit", "relaxed")
        );
    }

    private CatalogSearchResponse.Product richProduct(
            Object listPrice,
            Object rating,
            Object reviewCount,
            Object metadata,
            Object techSpecs
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

    private Object deeplyNestedValue(int depth) {
        Object value = "GOTS";
        for (int index = 0; index < depth; index++) {
            value = List.of(value);
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
            super(null);
        }

        @Override
        public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
            return results.get(merchantId);
        }
    }

    static class FakeMerchantCatalogSearchClient extends MerchantCatalogSearchClient {

        private final Map<String, CatalogSearchResult> results = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private CountDownLatch concurrentCatalogSearches;

        FakeMerchantCatalogSearchClient() {
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
            calls.add(merchant.domain() + ":" + query + ":" + limit);
            awaitConcurrentCalls(concurrentCatalogSearches, "Catalog search");
            if (failures.containsKey(merchant.domain())) {
                throw new MerchantCatalogSearchException(failures.get(merchant.domain()));
            }
            return results.get(merchant.domain());
        }
    }

    static class FakeMerchantProductDetailsClient extends MerchantProductDetailsClient {

        private final Map<String, String> failures = new HashMap<>();
        private final Map<String, String> prices = new HashMap<>();
        private final Map<String, String> currencies = new HashMap<>();
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private CountDownLatch concurrentProductDetails;

        FakeMerchantProductDetailsClient() {
            super(null, null);
        }

        @Override
        public ProductDetailsResult getProductDetails(
                MerchantSemanticSearchResult merchant,
                String productId,
                CatalogSearchContext context
        ) {
            calls.add(merchant.domain() + ":" + productId);
            awaitConcurrentCalls(concurrentProductDetails, "Product details");
            if (failures.containsKey(productId)) {
                throw new MerchantProductDetailsException(failures.get(productId));
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
