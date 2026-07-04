package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.repository.ReviewProviderDiscoveryCandidateRepository;
import com.meant.api.module.review.service.command.DiscoverReviewProvidersCommand;
import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ReviewProviderDiscoveryServiceTest {

    private final FakeCandidateRepository candidateRepository = new FakeCandidateRepository();
    private final FakeStorefrontClient storefrontClient = new FakeStorefrontClient();
    private final CapturingPersistenceService persistenceService = new CapturingPersistenceService();
    private final ReviewProviderDiscoveryProperties properties = new ReviewProviderDiscoveryProperties(
            10,
            Duration.ofHours(1),
            Duration.ofSeconds(5),
            2 * 1024 * 1024,
            Duration.ofMinutes(10),
            Duration.ofDays(1),
            Duration.ofDays(14)
    );
    private final ReviewProviderDiscoveryService discoveryService = new ReviewProviderDiscoveryService(
            candidateRepository,
            storefrontClient,
            new ReviewProductPageLinkExtractor(),
            new ReviewProviderDetectionService(),
            persistenceService,
            properties
    );

    @Test
    void persistsNotFoundWhenNoProviderIsFound() {
        ReviewProviderDiscoveryCandidate candidate = candidate();
        URI storefrontUri = URI.create("https://merchant.example/");
        candidateRepository.candidates = List.of(candidate);
        storefrontClient.documents.add(new StorefrontDocument(
                storefrontUri.toString(),
                "<html>No review provider here.</html>"
        ));

        discoveryService.discoverProviders(new DiscoverReviewProvidersCommand(1));

        assertThat(persistenceService.notFoundCandidate).isEqualTo(candidate);
        assertThat(persistenceService.detectedCandidate).isNull();
    }

    @Test
    void persistsDetectedKlaviyoProviderWhenMarkersAreFound() {
        ReviewProviderDiscoveryCandidate candidate = candidate();
        URI storefrontUri = URI.create("https://merchant.example/");
        candidateRepository.candidates = List.of(candidate);
        storefrontClient.documents.add(new StorefrontDocument(
                storefrontUri.toString(),
                """
                        <script async src="https://static.klaviyo.com/onsite/js/J5feSG/klaviyo.js?company_id=J5feSG"></script>
                        <div id="kl_reviews"></div>
                        """
        ));

        discoveryService.discoverProviders(new DiscoverReviewProvidersCommand(1));

        assertThat(persistenceService.detectedCandidate).isEqualTo(candidate);
        assertThat(persistenceService.detection.provider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(persistenceService.detection.providerKey()).isEqualTo("J5feSG");
    }

    @Test
    void claimsCandidatesWithClaimDurationInsteadOfRetryDelay() {
        ReviewProviderDiscoveryCandidate candidate = candidate();
        candidateRepository.candidates = List.of(candidate);
        storefrontClient.documents.add(new StorefrontDocument(
                "https://merchant.example/",
                "<html>No review provider here.</html>"
        ));

        discoveryService.discoverProviders(new DiscoverReviewProvidersCommand(1));

        assertThat(candidateRepository.claimExpiresAt)
                .isBefore(candidateRepository.now.plus(properties.retryDelay()))
                .isEqualTo(candidateRepository.now.plus(properties.claimDuration()));
    }

    private ReviewProviderDiscoveryCandidate candidate() {
        return new ReviewProviderDiscoveryCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "merchant.example"
        );
    }

    private static class FakeCandidateRepository extends ReviewProviderDiscoveryCandidateRepository {

        private List<ReviewProviderDiscoveryCandidate> candidates = List.of();
        private Instant now;
        private Instant claimExpiresAt;

        private FakeCandidateRepository() {
            super(null, null);
        }

        @Override
        public List<ReviewProviderDiscoveryCandidate> claimCandidates(Instant now, Instant claimExpiresAt, int limit) {
            this.now = now;
            this.claimExpiresAt = claimExpiresAt;
            return candidates;
        }
    }

    private static class FakeStorefrontClient extends ReviewStorefrontClient {

        private final List<StorefrontDocument> documents = new ArrayList<>();

        private FakeStorefrontClient() {
            super(RestClient.builder().build());
        }

        @Override
        public StorefrontDocument fetch(URI uri) {
            return documents.removeFirst();
        }
    }

    private static class CapturingPersistenceService extends ReviewProviderPersistenceService {

        private ReviewProviderDiscoveryCandidate detectedCandidate;
        private ReviewProviderDetectionResult detection;
        private ReviewProviderDiscoveryCandidate notFoundCandidate;

        private CapturingPersistenceService() {
            super(null);
        }

        @Override
        public void persistDetected(
                ReviewProviderDiscoveryCandidate candidate,
                ReviewProviderDetectionResult detection,
                Instant checkedAt
        ) {
            this.detectedCandidate = candidate;
            this.detection = detection;
        }

        @Override
        public void persistNotFound(
                ReviewProviderDiscoveryCandidate candidate,
                Instant checkedAt,
                Instant nextCheckAt
        ) {
            this.notFoundCandidate = candidate;
        }

        @Override
        public void persistRetryableFailure(
                ReviewProviderDiscoveryCandidate candidate,
                Throwable exception,
                Instant checkedAt,
                Instant nextCheckAt
        ) {
            throw new AssertionError("Retryable failure was not expected", exception);
        }
    }
}
