package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.properties.CrawlingProperties;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.HuggingFaceDatasetRow;
import com.meant.api.module.merchant.service.dto.UcpMerchantDatasetRow;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@SpringBootTest
class UcpMerchantImportServiceIT extends PostgresIntegrationTestSupport {

    @Autowired
    private UcpMerchantImportService service;

    @Autowired
    private MerchantRawRepository repository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantIdentityRepository merchantIdentityRepository;

    @Autowired
    private MerchantIntegrationRepository merchantIntegrationRepository;

    @Autowired
    private FakeUcpDatasetClient datasetClient;

    @BeforeEach
    void setUp() {
        merchantIntegrationRepository.deleteAllInBatch();
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
                .extracting(MerchantRaw::getSource)
                .containsOnly(MerchantRawSource.HUGGING_FACE);
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
        MerchantRaw imported = huggingFaceMerchant("verified.example");
        imported.markProcessed("PROCESSED_UPDATED", Instant.parse("2026-04-03T09:00:15Z"));
        repository.save(imported);

        service.importMerchants();

        MerchantRaw updated = huggingFaceMerchant("verified.example");
        assertThat(updated.isProcessed()).isTrue();
        assertThat(updated.getProcessingStatus()).isEqualTo("PROCESSED_UPDATED");
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void importMerchantsResetsProcessedStateWhenSourceHashChanges() {
        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"));
        service.importMerchants();
        MerchantRaw imported = huggingFaceMerchant("verified.example");
        imported.markProcessed("PROCESSED_UPDATED", Instant.parse("2026-04-03T09:00:15Z"));
        repository.save(imported);

        datasetClient.rows = List.of(datasetRow(1, "verified.example", "verified", "{\"GPTBot\": false}", "[\"mcp\"]"));
        service.importMerchants();

        MerchantRaw updated = huggingFaceMerchant("verified.example");
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

        MerchantRaw oldMerchant = huggingFaceMerchant("existing.example");
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

        MerchantRaw oldMerchantRaw = huggingFaceMerchant("existing.example");
        Merchant oldMerchant = merchantRepository.findByDomain("existing.example").orElseThrow();
        assertThat(oldMerchantRaw.isActive()).isFalse();
        assertThat(oldMerchantRaw.getProcessingStatus()).isEqualTo("INACTIVE");
        assertThat(oldMerchant.isActive()).isFalse();
        assertThat(oldMerchant.getUpdatedAt()).isAfter(Instant.parse("2026-04-02T09:00:15Z"));
    }

    @Test
    void importMerchantsDeduplicatesVerifiedRowsByDomain() {
        datasetClient.rows = List.of(
                datasetRow(1, "WWW.Duplicate.Example.", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "duplicate.example", "verified", "{\"GPTBot\": false}", "[\"embedded\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll()).hasSize(1);
        MerchantRaw imported = huggingFaceMerchant("duplicate.example");
        assertThat(imported.getDatasetRowIdx()).isEqualTo(1);
        assertThat(imported.getDomain()).isEqualTo("duplicate.example");
        assertThat(imported.getUcpUrl())
                .isEqualTo("https://WWW.Duplicate.Example./.well-known/ucp");
        assertThat(imported.getAiBotPolicies()).isEqualTo("{\"GPTBot\": true}");
        assertThat(imported.getTransports()).isEqualTo("[\"mcp\"]");
    }

    @Test
    void importMerchantsMatchesNormalizedDomainsAcrossRunsWithoutRewritingTheUcpUrl() {
        datasetClient.rows = List.of(
                datasetRow(1, "WWW.Verified.Example.", "verified", "{}", "[\"mcp\"]")
        );
        service.importMerchants();
        MerchantRaw firstImport = huggingFaceMerchant("verified.example");

        datasetClient.rows = List.of(
                datasetRow(2, "verified.example", "verified", "{}", "[\"mcp\"]")
        );
        service.importMerchants();

        MerchantRaw secondImport = huggingFaceMerchant("verified.example");
        assertThat(repository.findBySourceAndDomainIn(
                MerchantRawSource.HUGGING_FACE,
                List.of("verified.example")
        )).hasSize(1);
        assertThat(secondImport.getId()).isEqualTo(firstImport.getId());
        assertThat(secondImport.getDomain()).isEqualTo("verified.example");
        assertThat(secondImport.getUcpUrl())
                .isEqualTo("https://verified.example/.well-known/ucp");
    }

    @Test
    void importMerchantsSkipsExcludedDomains() {
        datasetClient.rows = List.of(
                datasetRow(1, "WWW.UCPCHECKER.COM.", "verified", "{\"GPTBot\": true}", "[\"mcp\"]"),
                datasetRow(2, "verified.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll())
                .extracting(MerchantRaw::getDomain)
                .containsExactly("verified.example");
    }

    @Test
    void importMerchantsKeepsObservationAndDatasetRowsSeparateForTheSameDomain() {
        MerchantRaw observed = repository.save(observedMerchant("shared.example", "gid://shopify/Shop/1"));
        datasetClient.rows = List.of(
                datasetRow(1, "shared.example", "verified", "{\"GPTBot\": true}", "[\"mcp\"]")
        );

        service.importMerchants();

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.HUGGING_FACE, "shared.example"))
                .hasValueSatisfying(imported -> {
                    assertThat(imported.getDatasetRowIdx()).isEqualTo(1);
                    assertThat(imported.getStatus()).isEqualTo("verified");
                });
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, "shared.example"))
                .hasValueSatisfying(savedObservation -> {
                    assertThat(savedObservation.getId()).isEqualTo(observed.getId());
                    assertThat(savedObservation.getObservedProvider()).isEqualTo(MerchantIntegrationProvider.SHOPIFY);
                    assertThat(savedObservation.getObservedExternalMerchantId()).isEqualTo("gid://shopify/Shop/1");
                    assertThat(savedObservation.isActive()).isTrue();
                });
    }

    @Test
    void importMerchantsDeactivatesOnlyMissingHuggingFaceRows() {
        repository.save(existingMerchant());
        repository.save(observedMerchant("observed.example", "gid://shopify/Shop/2"));

        service.importMerchants();

        assertThat(huggingFaceMerchant("existing.example").isActive()).isFalse();
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, "observed.example"))
                .hasValueSatisfying(observed -> assertThat(observed.isActive()).isTrue());
    }

    @Test
    void importMerchantsDeactivatesAnUnverifiedObservationOnlyMerchant() {
        MerchantRaw imported = repository.save(existingMerchant());
        Merchant merchant = merchantRepository.save(profiledMerchant(imported));
        imported.linkMerchant(merchant);
        repository.save(imported);
        MerchantRaw observed = observedMerchant("existing.example", "gid://shopify/Shop/3");
        observed.linkMerchant(merchant);
        repository.save(observed);

        service.importMerchants();

        assertThat(huggingFaceMerchant("existing.example").isActive()).isFalse();
        assertThat(repository.findBySourceAndDomain(
                MerchantRawSource.SHOPIFY_OBSERVATION,
                "existing.example"
        )).hasValueSatisfying(source -> assertThat(source.isActive()).isTrue());
        assertThat(merchantRepository.findById(merchant.getId()))
                .hasValueSatisfying(savedMerchant -> assertThat(savedMerchant.isActive()).isFalse());
    }

    @Test
    void importMerchantsKeepsVerifiedRoutableObservationOwnerActive() {
        MerchantRaw imported = repository.save(existingMerchant());
        Merchant merchant = merchantRepository.save(profiledMerchant(imported));
        imported.linkMerchant(merchant);
        repository.save(imported);
        MerchantRaw observed = observedMerchant("existing.example", "gid://shopify/Shop/3");
        observed.linkMerchant(merchant);
        repository.save(observed);
        merchantIdentityRepository.save(MerchantIdentity.builder()
                .merchant(merchant)
                .namespace(MerchantIdentityNamespace.DOMAIN)
                .normalizedValue("existing.example")
                .role(MerchantIdentityRole.STOREFRONT_DOMAIN)
                .source(MerchantRawSource.SHOPIFY_OBSERVATION)
                .verifiedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build());
        saveActiveCatalogIntegration(merchant);

        service.importMerchants();

        assertThat(merchantRepository.findById(merchant.getId()))
                .hasValueSatisfying(savedMerchant -> assertThat(savedMerchant.isActive()).isTrue());
    }

    private void saveActiveCatalogIntegration(Merchant merchant) {
        Instant capturedAt = Instant.parse("2026-04-02T09:00:15Z");
        merchantIntegrationRepository.save(MerchantIntegration.builder()
                .merchant(merchant)
                .provider(MerchantIntegrationProvider.GENERIC_UCP)
                .kind(MerchantIntegrationKind.MERCHANT_CONNECTION)
                .roles(EnumSet.of(MerchantIntegrationRole.STOREFRONT_CATALOG))
                .endpoint("https://existing.example/api/ucp/mcp")
                .protocolVersion("2026-01-23")
                .authStrategy(MerchantIntegrationAuthStrategy.NONE)
                .status(MerchantIntegrationStatus.ACTIVE)
                .source(MerchantIntegrationSource.DISCOVERY)
                .capturedAt(capturedAt)
                .createdAt(capturedAt)
                .updatedAt(capturedAt)
                .build());
    }

    private MerchantRaw huggingFaceMerchant(String domain) {
        return repository.findBySourceAndDomain(MerchantRawSource.HUGGING_FACE, domain).orElseThrow();
    }

    private MerchantRaw existingMerchant() {
        return MerchantRaw.builder()
                .source(MerchantRawSource.HUGGING_FACE)
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

    private MerchantRaw observedMerchant(String domain, String externalMerchantId) {
        Instant observedAt = Instant.parse("2026-04-02T09:00:15Z");
        return MerchantRaw.builder()
                .source(MerchantRawSource.SHOPIFY_OBSERVATION)
                .observedProvider(MerchantIntegrationProvider.SHOPIFY)
                .observedExternalMerchantId(externalMerchantId)
                .domain(domain)
                .status("observed")
                .ucpUrl("https://%s/.well-known/ucp".formatted(domain))
                .capabilityCount(0)
                .transports("mcp")
                .fetchedAt(observedAt)
                .processed(false)
                .sourceHash("observed-source-hash-" + externalMerchantId)
                .active(true)
                .lastSeenAt(observedAt)
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
