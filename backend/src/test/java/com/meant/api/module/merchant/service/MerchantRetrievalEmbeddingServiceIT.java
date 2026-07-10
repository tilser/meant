package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCategory;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingRepository;
import com.meant.api.module.merchant.service.command.GenerateMerchantRetrievalEmbeddingsCommand;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@SpringBootTest
class MerchantRetrievalEmbeddingServiceIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MerchantRetrievalEmbeddingService merchantRetrievalEmbeddingService;

    @Autowired
    private MerchantSemanticSearchService merchantSemanticSearchService;

    @Autowired
    private MerchantRetrievalContentBuilder merchantRetrievalContentBuilder;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantCategoryRepository merchantCategoryRepository;

    @Autowired
    private MerchantPopularSearchRepository merchantPopularSearchRepository;

    @Autowired
    private MerchantRetrievalEmbeddingRepository merchantRetrievalEmbeddingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeVoyageEmbeddingClient voyageEmbeddingClient;

    @Autowired
    private FakeVoyageRerankClient voyageRerankClient;

    @BeforeEach
    void setUp() {
        merchantRetrievalEmbeddingRepository.deleteAllInBatch();
        merchantCategoryRepository.deleteAllInBatch();
        merchantPopularSearchRepository.deleteAllInBatch();
        merchantRepository.deleteAllInBatch();
        merchantRawRepository.deleteAllInBatch();
        voyageEmbeddingClient.reset();
        voyageRerankClient.reset();
    }

    @Test
    void contentBuilderUsesOnlyCategoriesAndPopularSearches() {
        Merchant merchant = saveMerchant("allbirds.com", "Noise description", "Noise about", "Noise audience");
        merchantCategoryRepository.save(MerchantCategory.builder()
                .merchant(merchant)
                .name("Shoes")
                .normalizedName("shoes")
                .build());
        merchantCategoryRepository.save(MerchantCategory.builder()
                .merchant(merchant)
                .name(" Apparel ")
                .normalizedName("apparel")
                .build());
        merchantPopularSearchRepository.save(MerchantPopularSearch.builder()
                .merchant(merchant)
                .searchText("wool runners")
                .build());
        merchantPopularSearchRepository.save(MerchantPopularSearch.builder()
                .merchant(merchant)
                .searchText("sneakers")
                .build());

        String content = merchantRetrievalContentBuilder.build(merchant).orElseThrow();

        assertThat(content).isEqualTo("""
                Categories: Apparel, Shoes
                Popular searches: sneakers, wool runners""");
        assertThat(content)
                .doesNotContain("Noise description")
                .doesNotContain("Noise about")
                .doesNotContain("Noise audience");
    }

    @Test
    void emptyContentDoesNotCreateEmbedding() {
        saveMerchant("empty.example", "Useful things", "About useful things", "Everyone");

        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        assertThat(voyageEmbeddingClient.documentCalls).isZero();
        assertThat(merchantRetrievalEmbeddingRepository.findAll()).isEmpty();
    }

    @Test
    void unchangedContentHashDoesNotCallVoyageAgain() {
        Merchant merchant = saveMerchantWithRetrievalContent("allbirds.com", "Shoes", "wool runners");
        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));
        assertThat(voyageEmbeddingClient.documentCalls).isEqualTo(1);
        touchMerchant(merchant.getId(), Instant.now().plusSeconds(60));

        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        assertThat(voyageEmbeddingClient.documentCalls).isEqualTo(1);
        assertThat(merchantRetrievalEmbeddingRepository.findAll()).hasSize(1);
    }

    @Test
    void changedContentUpdatesEmbedding() {
        Merchant merchant = saveMerchantWithRetrievalContent("allbirds.com", "Shoes", "wool runners");
        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));
        merchantPopularSearchRepository.save(MerchantPopularSearch.builder()
                .merchant(merchant)
                .searchText("trail runners")
                .build());
        touchMerchant(merchant.getId(), Instant.now().plusSeconds(60));

        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        assertThat(voyageEmbeddingClient.documentCalls).isEqualTo(2);
        assertThat(merchantRetrievalEmbeddingRepository.findByMerchantId(merchant.getId()).orElseThrow().getRetrievalContent())
                .contains("trail runners");
    }

    @Test
    void generateRetrievalEmbeddingsBatchesMultipleMerchantInputs() {
        saveMerchantWithRetrievalContent("shoe-store.example", "Shoes", "running shoes");
        saveMerchantWithRetrievalContent("home-store.example", "Home", "kitchen table");

        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        assertThat(voyageEmbeddingClient.documentCalls).isEqualTo(1);
        assertThat(voyageEmbeddingClient.documentBatchSizes).containsExactly(2);
        assertThat(merchantRetrievalEmbeddingRepository.findAll()).hasSize(2);
    }

    @Test
    void semanticSearchReturnsNearestMerchantCandidates() {
        saveMerchantWithRetrievalContent("shoe-store.example", "Shoes", "running shoes");
        saveMerchantWithRetrievalContent("home-store.example", "Home", "kitchen table");
        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        List<MerchantSemanticSearchResult> candidates = merchantSemanticSearchService.search(
                new SemanticMerchantSearchQuery("running shoes", 2)
        );

        assertThat(voyageRerankClient.rerankCalls).isEqualTo(1);
        assertThat(candidates).extracting(MerchantSemanticSearchResult::domain)
                .containsExactly("shoe-store.example", "home-store.example");
        assertThat(candidates.getFirst().semanticScore()).isGreaterThan(candidates.getLast().semanticScore());
        assertThat(candidates.getFirst().rerankScore()).isGreaterThan(candidates.getLast().rerankScore());
        assertThat(candidates).extracting(MerchantSemanticSearchResult::rank)
                .containsExactly(1, 2);
    }

    private Merchant saveMerchantWithRetrievalContent(String domain, String category, String popularSearch) {
        Merchant merchant = saveMerchant(domain, "Description", "About", "Audience");
        merchantCategoryRepository.save(MerchantCategory.builder()
                .merchant(merchant)
                .name(category)
                .normalizedName(category.toLowerCase(Locale.ROOT))
                .build());
        merchantPopularSearchRepository.save(MerchantPopularSearch.builder()
                .merchant(merchant)
                .searchText(popularSearch)
                .build());
        return merchant;
    }

    private Merchant saveMerchant(String domain, String description, String about, String targetAudience) {
        MerchantRaw merchantRaw = merchantRawRepository.save(MerchantRaw.builder()
                .datasetRowIdx(1)
                .domain(domain)
                .status("verified")
                .ucpUrl("https://%s/.well-known/ucp".formatted(domain))
                .httpStatus(200)
                .ucpVersion("2026-01-23")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(true)
                .hasPaymentToken(true)
                .capabilityCount(4)
                .aiBotPolicies("{}")
                .transports("[\"mcp\"]")
                .lastCheckedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .lastSuccessAt(Instant.parse("2026-04-02T09:00:15Z"))
                .fetchedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .processed(true)
                .sourceHash("source-hash-" + UUID.randomUUID())
                .active(true)
                .lastSeenAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build());
        return merchantRepository.save(Merchant.builder()
                .merchantRaw(merchantRaw)
                .domain(domain)
                .ucpUrl(merchantRaw.getUcpUrl())
                .ucpVersion(merchantRaw.getUcpVersion())
                .profileHash("profile-hash-" + UUID.randomUUID())
                .name(domain)
                .description(description)
                .about(about)
                .targetAudience(targetAudience)
                .profileQuestion("Tell me about your store?")
                .profileAnswerRaw("Profile answer should not be embedded.")
                .active(true)
                .lastProfiledAt(Instant.parse("2026-04-02T09:00:15Z"))
                .createdAt(Instant.parse("2026-04-02T09:00:15Z"))
                .updatedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build());
    }

    private void touchMerchant(UUID merchantId, Instant updatedAt) {
        jdbcTemplate.update("update merchant set updated_at = ? where id = ?", Timestamp.from(updatedAt), merchantId);
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        @Primary
        FakeVoyageEmbeddingClient fakeVoyageEmbeddingClient(MerchantEmbeddingProperties merchantEmbeddingProperties) {
            return new FakeVoyageEmbeddingClient(merchantEmbeddingProperties);
        }

        @Bean
        @Primary
        FakeVoyageRerankClient fakeVoyageRerankClient(MerchantEmbeddingProperties merchantEmbeddingProperties) {
            return new FakeVoyageRerankClient(merchantEmbeddingProperties);
        }
    }

    static class FakeVoyageEmbeddingClient extends VoyageEmbeddingClient {

        private final int dimension;
        private int documentCalls;
        private final List<Integer> documentBatchSizes = new ArrayList<>();

        FakeVoyageEmbeddingClient(MerchantEmbeddingProperties merchantEmbeddingProperties) {
            super(RestClient.builder(), merchantEmbeddingProperties);
            this.dimension = merchantEmbeddingProperties.dimension();
        }

        @Override
        public List<List<Double>> embedDocuments(List<String> texts) {
            documentCalls++;
            documentBatchSizes.add(texts.size());
            return texts.stream()
                    .map(this::embeddingForText)
                    .toList();
        }

        @Override
        public List<Double> embedQuery(String query) {
            return embeddingForText(query);
        }

        private void reset() {
            documentCalls = 0;
            documentBatchSizes.clear();
        }

        private List<Double> embeddingForText(String text) {
            List<Double> embedding = new ArrayList<>(Collections.nCopies(dimension, 0.0d));
            String normalizedText = text.toLowerCase(Locale.ROOT);
            if (normalizedText.contains("shoe") || normalizedText.contains("runner")) {
                embedding.set(0, 1.0d);
                return embedding;
            }
            embedding.set(1, 1.0d);
            return embedding;
        }
    }

    static class FakeVoyageRerankClient extends VoyageRerankClient {

        private int rerankCalls;

        FakeVoyageRerankClient(MerchantEmbeddingProperties merchantEmbeddingProperties) {
            super(RestClient.builder(), merchantEmbeddingProperties);
        }

        @Override
        public List<VoyageRerankResult> rerank(String query, List<String> documents) {
            rerankCalls++;
            List<VoyageRerankResult> results = new ArrayList<>();
            for (int index = documents.size() - 1; index >= 0; index--) {
                results.add(new VoyageRerankResult(index, relevanceScore(query, documents.get(index))));
            }
            return results;
        }

        private void reset() {
            rerankCalls = 0;
        }

        private double relevanceScore(String query, String document) {
            String normalizedQuery = query.toLowerCase(Locale.ROOT);
            String normalizedDocument = document.toLowerCase(Locale.ROOT);
            if (normalizedQuery.contains("running") && normalizedDocument.contains("running")) {
                return 0.9d;
            }
            if (normalizedQuery.contains("shoe") && normalizedDocument.contains("shoe")) {
                return 0.6d;
            }
            return 0.1d;
        }
    }
}
