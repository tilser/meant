package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
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
    void observesMultipleDomainsIdempotentlyAndLaterAcceptsImportedDatasetIndex() {
        String firstDomain = "observed-one.example";
        String secondDomain = "observed-two.example";

        service.enqueue(firstDomain);
        service.enqueue(secondDomain);
        service.enqueue(firstDomain);

        var observed = repository.findByDomainIn(List.of(firstDomain, secondDomain));
        assertThat(observed).hasSize(2).allSatisfy(row -> assertThat(row.getDatasetRowIdx()).isNull());

        var first = repository.findByDomain(firstDomain).orElseThrow();
        Instant importedAt = Instant.parse("2026-07-11T12:00:00Z");
        first.updateFromImport(
                8123, "ok", "https://observed-one.example/.well-known/ucp", 200, "2026-04-08",
                false, false, true, false, false, 1, null, "mcp",
                importedAt, importedAt, importedAt, "imported-source-hash");
        repository.saveAndFlush(first);

        assertThat(repository.findByDomain(firstDomain).orElseThrow().getDatasetRowIdx()).isEqualTo(8123);
        repository.deleteAll(observed);
    }
}
