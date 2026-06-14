package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MerchantSemanticProductSearchServiceTest {

    private FakeMerchantSemanticSearchService merchantSemanticSearchService;
    private FakeMerchantCatalogSearchClient merchantCatalogSearchClient;
    private FakeVoyageRerankClient voyageRerankClient;
    private MerchantSemanticProductSearchService merchantSemanticProductSearchService;

    @BeforeEach
    void setUp() {
        merchantSemanticSearchService = new FakeMerchantSemanticSearchService();
        merchantCatalogSearchClient = new FakeMerchantCatalogSearchClient();
        voyageRerankClient = new FakeVoyageRerankClient();
        merchantSemanticProductSearchService = new MerchantSemanticProductSearchService(
                merchantSemanticSearchService,
                merchantCatalogSearchClient,
                voyageRerankClient,
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
                new SemanticProductSearchQuery("running shoes", null, null, null, null)
        );

        assertThat(merchantSemanticSearchService.lastQuery.limit()).isEqualTo(3);
        assertThat(merchantCatalogSearchClient.calls).containsExactly(
                "home.example:running shoes:2",
                "shoe.example:running shoes:2"
        );
        assertThat(voyageRerankClient.documents).hasSize(4);
        assertThat(result.merchants()).extracting("domain")
                .containsExactly("home.example", "shoe.example");
        assertThat(result.products()).extracting("productId")
                .containsExactly("trail-runner", "casual-sneaker");
        assertThat(result.products()).extracting("rank")
                .containsExactly(1, 2);
        assertThat(result.products().getFirst().merchantDomain()).isEqualTo("shoe.example");
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
                new SemanticProductSearchQuery("running shoes", null, null, null, null)
        );

        assertThat(result.merchants()).hasSize(2);
        assertThat(result.merchants().getFirst().error()).contains("catalog unavailable");
        assertThat(result.merchants().getLast().productCount()).isEqualTo(1);
        assertThat(result.products()).extracting("productId").containsExactly("runner");
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

    private CatalogSearchResponse.Product product(String id, String title, String description, String category) {
        return new CatalogSearchResponse.Product(
                id,
                title,
                new CatalogSearchResponse.Description("<p>" + description + "</p>"),
                "https://example.com/products/" + id,
                new CatalogSearchResponse.PriceRange(
                        new CatalogSearchResponse.Money(1000L, "USD"),
                        new CatalogSearchResponse.Money(1000L, "USD")
                ),
                List.of(new CatalogSearchResponse.Variant(
                        id + "-variant",
                        "Default Title",
                        new CatalogSearchResponse.Description("<p>" + description + "</p>"),
                        new CatalogSearchResponse.Money(1000L, "USD"),
                        new CatalogSearchResponse.Availability(true),
                        List.of(new CatalogSearchResponse.Media("image", "https://example.com/" + id + ".jpg"))
                )),
                List.of(new CatalogSearchResponse.Media("image", "https://example.com/" + id + ".jpg")),
                List.of(new CatalogSearchResponse.Category(category, "shopify")),
                List.of(category)
        );
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

    static class FakeMerchantCatalogSearchClient extends MerchantCatalogSearchClient {

        private final Map<String, CatalogSearchResult> results = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private final List<String> calls = new ArrayList<>();

        FakeMerchantCatalogSearchClient() {
            super(RestClient.builder(), null);
        }

        @Override
        public CatalogSearchResult searchCatalog(MerchantSemanticSearchResult merchant, String query, int limit) {
            calls.add(merchant.domain() + ":" + query + ":" + limit);
            if (failures.containsKey(merchant.domain())) {
                throw new MerchantCatalogSearchException(failures.get(merchant.domain()));
            }
            return results.get(merchant.domain());
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
