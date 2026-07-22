package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MerchantEnrichmentCandidateServiceIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MerchantEnrichmentCandidateService service;

    @Autowired
    private MerchantRawRepository repository;

    @Test
    void observesMultipleDomainsIdempotentlyWithoutMergingTheDatasetSource() {
        String firstDomain = "observed-one.example";
        String secondDomain = "observed-two.example";
        String firstMerchantId = "gid://shopify/Shop/1";

        service.enqueue(firstDomain, MerchantIntegrationProvider.SHOPIFY, firstMerchantId);
        service.enqueue(secondDomain, MerchantIntegrationProvider.SHOPIFY, "gid://shopify/Shop/2");
        service.enqueue(firstDomain, MerchantIntegrationProvider.SHOPIFY, firstMerchantId);

        var observed = repository.findBySourceAndDomainIn(
                MerchantRawSource.SHOPIFY_OBSERVATION,
                List.of(firstDomain, secondDomain)
        );
        assertThat(observed).hasSize(2).allSatisfy(row -> {
            assertThat(row.getDatasetRowIdx()).isNull();
            assertThat(row.getObservedProvider()).isEqualTo(MerchantIntegrationProvider.SHOPIFY);
        });
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, firstDomain))
                .hasValueSatisfying(first ->
                        assertThat(first.getObservedExternalMerchantId()).isEqualTo(firstMerchantId));

        assertThatThrownBy(() -> service.enqueue(
                firstDomain,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/999"
        )).isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("another external merchant identity");
        assertThatThrownBy(() -> service.enqueue(
                firstDomain,
                MerchantIntegrationProvider.GENERIC_UCP,
                firstMerchantId
        )).isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("another provider");
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, firstDomain))
                .hasValueSatisfying(first ->
                        assertThat(first.getObservedExternalMerchantId()).isEqualTo(firstMerchantId));

        Instant importedAt = Instant.parse("2026-07-11T12:00:00Z");
        repository.saveAndFlush(MerchantRaw.builder()
                .source(MerchantRawSource.HUGGING_FACE)
                .datasetRowIdx(8123)
                .domain(firstDomain)
                .status("verified")
                .ucpUrl("https://observed-one.example/.well-known/ucp")
                .httpStatus(200)
                .ucpVersion("2026-04-08")
                .hasCartManagement(true)
                .capabilityCount(1)
                .transports("mcp")
                .fetchedAt(importedAt)
                .processed(false)
                .sourceHash("imported-source-hash")
                .active(true)
                .lastSeenAt(importedAt)
                .build());

        assertThat(repository.findBySourceAndDomain(MerchantRawSource.HUGGING_FACE, firstDomain))
                .hasValueSatisfying(first -> assertThat(first.getDatasetRowIdx()).isEqualTo(8123));
        assertThat(repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, firstDomain))
                .hasValueSatisfying(first -> assertThat(first.getDatasetRowIdx()).isNull());
        repository.deleteAllInBatch();
    }
}
