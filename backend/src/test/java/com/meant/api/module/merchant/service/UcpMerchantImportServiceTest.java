package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.CrawlingProperties;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
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
class UcpMerchantImportServiceTest {

    @Autowired
    private UcpMerchantImportService service;

    @Autowired
    private MerchantRawRepository repository;

    @Autowired
    private FakeUcpDatasetClient datasetClient;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        datasetClient.rows = List.of();
        datasetClient.exception = null;
    }

    @Test
    void importMerchantsSavesOnlyVerifiedRowsWithParsedJsonPayloads() {
        datasetClient.rows = List.of(
                datasetRow(1, "verified-one.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "unverified.example", "pending", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(3, "verified-two.example", "verified", "{\"GPTBot\": false}", "[\"embedded\"]")
        );

        UcpMerchantImportResult result = service.importMerchants();

        assertThat(result.fetchedRows()).isEqualTo(3);
        assertThat(result.savedRows()).isEqualTo(2);
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getDomain)
                .containsExactlyInAnyOrder("verified-one.example", "verified-two.example");
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getStatus)
                .containsOnly("verified");
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getAiBotPolicies)
                .allSatisfy(value -> assertThat(value).contains("GPTBot"));
        assertThat(repository.findAll())
                .extracting(MerchantRaw::getTransports)
                .containsExactlyInAnyOrder("[\"mcp\"]", "[\"embedded\"]");
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
