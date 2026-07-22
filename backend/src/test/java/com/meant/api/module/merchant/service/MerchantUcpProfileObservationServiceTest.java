package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.properties.MerchantUcpProfileObservationProperties;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantUcpProfileObservationServiceTest {

    @Test
    void cacheAndEnrichmentIdentityIncludeTheObservedProviderMerchant() {
        String domain = "shop.example";
        URI profileEndpoint = URI.create("https://shop.example/.well-known/ucp");
        FakeProfileClient profileClient = new FakeProfileClient(profileEndpoint);
        RecordingEnrichmentCandidates enrichmentCandidates = new RecordingEnrichmentCandidates();
        MerchantUcpProfileObservationService service = new MerchantUcpProfileObservationService(
                profileClient,
                enrichmentCandidates,
                new MerchantUcpProfileObservationProperties(Duration.ofMinutes(10), 50)
        );

        service.observe(
                domain,
                profileEndpoint,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/1"
        );
        service.observe(
                domain,
                profileEndpoint,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/1"
        );
        service.observe(
                domain,
                profileEndpoint,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/2"
        );

        assertThat(profileClient.fetchCount).isEqualTo(2);
        assertThat(enrichmentCandidates.observations).containsExactly(
                new CandidateObservation(domain, MerchantIntegrationProvider.SHOPIFY, "gid://shopify/Shop/1"),
                new CandidateObservation(domain, MerchantIntegrationProvider.SHOPIFY, "gid://shopify/Shop/2")
        );
    }

    private static class FakeProfileClient extends UcpProfileClient {
        private final URI endpoint;
        private int fetchCount;

        private FakeProfileClient(URI endpoint) {
            super(RestClient.builder(), new ObjectMapper());
            this.endpoint = endpoint;
        }

        @Override
        public UcpProfileFetchResult fetchProfileResult(String merchantDomain, String ucpUrl) {
            fetchCount++;
            return new UcpProfileFetchResult(
                    new UcpProfile("2026-04-08", Map.of(), Map.of(), Map.of(), Map.of()),
                    "{}",
                    endpoint.toString(),
                    Instant.parse("2026-07-22T12:00:00Z")
            );
        }
    }

    private static class RecordingEnrichmentCandidates extends MerchantEnrichmentCandidateService {
        private final List<CandidateObservation> observations = new ArrayList<>();

        private RecordingEnrichmentCandidates() {
            super(null);
        }

        @Override
        public void enqueue(
                String domain,
                MerchantIntegrationProvider provider,
                String externalMerchantId
        ) {
            observations.add(new CandidateObservation(domain, provider, externalMerchantId));
        }
    }

    private record CandidateObservation(
            String domain,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
    }
}
