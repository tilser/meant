package com.meant.api.module.review.service;

import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.repository.ReviewProviderDiscoveryCandidateRepository;
import com.meant.api.module.review.service.command.DiscoverReviewProvidersCommand;
import com.meant.api.module.review.service.dto.ReviewProviderDetectionResult;
import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import com.meant.api.module.review.service.dto.StorefrontDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Lazy
@Validated
@RequiredArgsConstructor
public class ReviewProviderDiscoveryService {

    private final ReviewProviderDiscoveryCandidateRepository candidateRepository;
    private final ReviewStorefrontClient storefrontClient;
    private final ReviewProductPageLinkExtractor productPageLinkExtractor;
    private final ReviewProviderDetectionService detectionService;
    private final ReviewProviderPersistenceService persistenceService;
    private final ReviewProviderDiscoveryProperties properties;

    public void discoverProviders(@NotNull @Valid DiscoverReviewProvidersCommand command) {
        Instant now = Instant.now();
        List<ReviewProviderDiscoveryCandidate> candidates = candidateRepository.claimCandidates(
                now,
                now.plus(properties.claimDuration()),
                command.batchSize()
        );
        for (ReviewProviderDiscoveryCandidate candidate : candidates) {
            discoverProvider(candidate);
        }
    }

    private void discoverProvider(ReviewProviderDiscoveryCandidate candidate) {
        Instant checkedAt = Instant.now();
        try {
            ReviewProviderDetectionResult detection = detect(candidate);
            if (detection.detected()) {
                persistenceService.persistDetected(candidate, detection, checkedAt);
                return;
            }
            persistenceService.persistNotFound(
                    candidate,
                    checkedAt,
                    checkedAt.plus(properties.notFoundRecheckDelay())
            );
        } catch (RuntimeException exception) {
            persistenceService.persistRetryableFailure(
                    candidate,
                    exception,
                    checkedAt,
                    checkedAt.plus(properties.retryDelay())
            );
        }
    }

    private ReviewProviderDetectionResult detect(ReviewProviderDiscoveryCandidate candidate) {
        URI storefrontUri = storefrontUri(candidate.merchantDomain());
        StorefrontDocument homepage = storefrontClient.fetch(storefrontUri);
        List<StorefrontDocument> documents = new ArrayList<>();
        documents.add(homepage);

        ReviewProviderDetectionResult homepageDetection = detectionService.detect(documents);
        if (homepageDetection.detected()) {
            return homepageDetection;
        }

        productPageLinkExtractor.firstProductPage(storefrontUri, homepage.html())
                .map(storefrontClient::fetch)
                .ifPresent(documents::add);
        return detectionService.detect(documents);
    }

    private URI storefrontUri(String merchantDomain) {
        String normalizedDomain = merchantDomain == null ? "" : merchantDomain.trim();
        if (normalizedDomain.startsWith("http://") || normalizedDomain.startsWith("https://")) {
            return URI.create(normalizedDomain);
        }
        return URI.create("https://" + normalizedDomain + "/");
    }
}
