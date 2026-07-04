package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.CrawlingProperties;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class UcpMerchantImportServiceTest extends PostgresIntegrationTest {

    @Autowired
    private UcpMerchantImportService service;

    @Autowired
    private MerchantRawRepository repository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private FakeUcpDatasetClient datasetClient;

    @BeforeEach
    void setUp() {
        merchantRepository.deleteAllInBatch();
        repository.deleteAllInBatch();
        datasetClient.rows = List.of();
        datasetClient.exception = null;
    }

    @Test
    void importMerchantsSavesOnlyVerifiedRowsWithRawJsonPayloads() {
        datasetClient.rows = List.of(
                datasetRow(1, "verified-one.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "unverified.example", "pending", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(3, "verified-two.example", "verified", "{\"GPTBot\": false}", "[\"embedded\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll())
                .extracting(MerchantRaw::getDomain)
                .containsExactlyInAnyOrder("verified-one.example", "verified-two.example");
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getStatus)
                .containsOnly("verified");
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getAiBotPolicies)
                .containsExactlyInAnyOrder("{\"GPTBot\": true}", "{\"GPTBot\": false}");
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getTransports)
                .containsExactlyInAnyOrder("[\"mcp\"]", "[\"embedded\"]");
        assertThat(repository.findAll())
                .allSatisfy(merchantRaw -> {
                    assertThat(merchantRaw.isActive()).isTrue();
                    assertThat(merchantRaw.isProcessed()).isFalse();
                    assertThat(merchantRaw.getSourceHash()).isNotBlank();
                    assertThat(merchantRaw.getLastSeenAt()).isNotNull();
                });
    }

    @Test
    void importMerchantsDoesNotReplaceRowsWhenFetchFails() {
        repository.save(existingMerchant());
        datasetClient.exception = new IllegalStateException("fetch failed");

        assertThatThrownBy(() -> service.importMerchants())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fetch failed");

        assertThat(repository.findAll())
                .extracting(MerchantRaw::getDomain)
                .containsExactly("existing.example");
    }

    @Test
    void importMerchantsKeepsProcessedStateWhenSourceHashIsUnchanged() {
        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"));
        service.importMerchants();
        MerchantRaw imported = repository.findByDomain("verified.example").orElseThrow();
        imported.markProcessed("PROCESSED_UPDATED", Instant.parse("2026-04-03T09:00:15Z"));
        repository.save(imported);

        service.importMerchants();

        MerchantRaw updated = repository.findByDomain("verified.example").orElseThrow();
        assertThat(updated.isProcessed()).isTrue();
        assertThat(updated.getProcessingStatus()).isEqualTo("PROCESSED_UPDATED");
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void importMerchantsResetsProcessedStateWhenSourceHashChanges() {
        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"));
        service.importMerchants();
        MerchantRaw imported = repository.findByDomain("verified.example").orElseThrow();
        imported.markProcessed("PROCESSED_UPDATED", Instant.parse("2026-04-03T09:00:15Z"));
        repository.save(imported);

        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": false}", "[\"mcp\"]"));
        service.importMerchants();

        MerchantRaw updated = repository.findByDomain("verified.example").orElseThrow();
        assertThat(updated.isProcessed()).isFalse();
        assertThat(updated.getProcessedAt()).isNull();
        assertThat(updated.getProcessingStatus()).isNull();
        assertThat(updated.getProcessingError()).isNull();
        assertThat(updated.getAiBotPolicies()).isEqualTo("{\"GPTBot\": false}");
    }

    @Test
    void importMerchantsMarksRowsMissingFromLatestImportInactive() {
        repository.save(existingMerchant());
        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"));

        service.importMerchants();

        MerchantRaw oldMerchant = repository.findByDomain("existing.example").orElseThrow();
        assertThat(oldMerchant.isActive()).isFalse();
        assertThat(oldMerchant.isProcessed()).isFalse();
        assertThat(oldMerchant.getProcessingStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void importMerchantsMarksProfiledMerchantsMissingFromLatestImportInactive() {
        MerchantRaw existingMerchant = repository.save(existingMerchant());
        merchantRepository.save(profiledMerchant(existingMerchant));
        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"));

        service.importMerchants();

        MerchantRaw oldMerchantRaw = repository.findByDomain("existing.example").orElseThrow();
        Merchant oldMerchant = merchantRepository.findByDomain("existing.example").orElseThrow();
        assertThat(oldMerchantRaw.isActive()).isFalse();
        assertThat(oldMerchantRaw.getProcessingStatus()).isEqualTo("INACTIVE");
        assertThat(oldMerchant.isActive()).isFalse();
        assertThat(oldMerchant.getUpdatedAt()).isAfter(Instant.parse("2026-04-02T09:00:15Z"));
    }

    @Test
    void importMerchantsDeduplicatesVerifiedRowsByDomain() {
        datasetClient.rows = List.of(
                datasetRow(1, "duplicate.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "duplicate.example", "verified", "{\"GPTBot\": false}", "[\"embedded\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll()).hasSize(1);
        MerchantRaw imported = repository.findByDomain("duplicate.example").orElseThrow();
        assertThat(imported.getDatasetRowIdx()).isEqualTo(1);
        assertThat(imported.getAiBotPolicies()).isEqualTo("{\"GPTBot\": true}");
        assertThat(imported.getTransports()).isEqualTo("[\"mcp\"]");
    }

    @Test
    void importMerchantsSkipsExcludedDomains() {
        datasetClient.rows = List.of(
                datasetRow(1, "ucpchecker.com", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll())
                .extracting(MerchantRaw::getDomain)
                .containsExactly("verified.example");
    }

    private MerchantRaw existingMerchant() {
        return MerchantRaw.builder()
                .datasetRowIdx(100)
                .domain("existing.example")
                .status("verified")
                .ucpUrl("https://existing.example/.well-known/ucp")
                .httpStatus(200)
                .ucpVersion("2026-01-23")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(false)
                .hasOrder(true)
                .hasPaymentToken(false)
                .capabilityCount(2)
                .aiBotPolicies("{\"GPTBot\":true}")
                .transports("[\"mcp\"]")
                .lastCheckedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .lastSuccessAt(Instant.parse("2026-04-02T09:00:15Z"))
                .fetchedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .processed(true)
                .processedAt(Instant.parse("2026-04-03T09:00:15Z"))
                .processingStatus("PROCESSED_UPDATED")
                .sourceHash("existing-source-hash")
                .active(true)
                .lastSeenAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build();
    }

    private Merchant profiledMerchant(MerchantRaw merchantRaw) {
        return Merchant.builder()
                .merchantRaw(merchantRaw)
                .domain(merchantRaw.getDomain())
                .ucpUrl(merchantRaw.getUcpUrl())
                .ucpVersion(merchantRaw.getUcpVersion())
                .profileHash("existing-profile-hash")
                .name("Existing")
                .description("Existing description")
                .about("Existing about")
                .targetAudience("Existing audience")
                .profileQuestion("Tell me about your store?")
                .profileAnswerRaw("Existing profile answer")
                .active(true)
                .lastProfiledAt(Instant.parse("2026-04-02T09:00:15Z"))
                .createdAt(Instant.parse("2026-04-02T09:00:15Z"))
                .updatedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build();
    }

    private HuggingFaceDatasetRow datasetRow(
            int rowIdx,
            String domain,
            String status,
            String aiBotPolicies,
            String transports
    ) {
        return new HuggingFaceDatasetRow(
                rowIdx,
                new UcpMerchantDatasetRow(
                        domain,
                        status,
                        "https://%s/.well-known/ucp".formatted(domain),
                        200.0,
                        "2026-01-23",
                        1,
                        0,
                        0,
                        1,
                        0,
                        2,
                        aiBotPolicies,
                        transports,
                        Instant.parse("2026-04-02T09:00:15Z"),
                        Instant.parse("2026-04-02T09:00:15Z")
                ),
                List.of()
        );
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        @Primary
        FakeUcpDatasetClient fakeUcpDatasetClient() {
            return new FakeUcpDatasetClient();
        }
    }

    static class FakeUcpDatasetClient extends UcpDatasetClient {

        private List<HuggingFaceDatasetRow> rows = List.of();
        private RuntimeException exception;

        FakeUcpDatasetClient() {
            super(RestClient.builder(), new CrawlingProperties(
                    "https://datasets.example/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train",
                    100,
                    "0 0 3 2 * *",
                    "UTC"
            ));
        }

        @Override
        public List<HuggingFaceDatasetRow> fetchAllRows() {
            if (exception != null) {
                throw exception;
            }
            return rows;
        }
    }
}
