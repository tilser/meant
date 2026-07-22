package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enqueues a safely observed domain for the existing merchant enrichment pipeline. */
@Service
@RequiredArgsConstructor
public class MerchantEnrichmentCandidateService {
    private final MerchantRawRepository repository;

    @Transactional
    public void enqueue(
            String domain,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
        Instant now = Instant.now();
        MerchantRaw candidate = repository.findBySourceAndDomain(MerchantRawSource.SHOPIFY_OBSERVATION, domain)
                .orElseGet(() -> MerchantRaw.builder()
                        .source(MerchantRawSource.SHOPIFY_OBSERVATION)
                        .observedProvider(provider)
                        .observedExternalMerchantId(externalMerchantId)
                        .datasetRowIdx(null)
                        .domain(domain)
                        .status("observed")
                        .ucpUrl("https://" + domain + "/.well-known/ucp")
                        .capabilityCount(0)
                        .transports("mcp")
                        .fetchedAt(now)
                        .processed(false)
                        .sourceHash(hash(provider.name() + "\u001F" + externalMerchantId + "\u001F" + domain))
                        .active(true)
                        .lastSeenAt(now)
                        .build());
        validateObservedIdentity(candidate, provider, externalMerchantId);
        candidate.observeProviderIdentity(provider, externalMerchantId);
        candidate.markForEnrichment(now);
        repository.save(candidate);
    }

    private void validateObservedIdentity(
            MerchantRaw candidate,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
        if (candidate.getObservedProvider() != null && candidate.getObservedProvider() != provider) {
            throw new MerchantEnrichmentException(
                    "Observed merchant domain is already bound to another provider"
            );
        }
        if (candidate.getObservedExternalMerchantId() != null
                && !candidate.getObservedExternalMerchantId().equals(externalMerchantId)) {
            throw new MerchantEnrichmentException(
                    "Observed merchant domain is already bound to another external merchant identity"
            );
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
