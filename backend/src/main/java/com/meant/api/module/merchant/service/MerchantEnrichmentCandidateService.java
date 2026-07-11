package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
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
    public void enqueue(String domain) {
        Instant now = Instant.now();
        MerchantRaw candidate = repository.findByDomain(domain).orElseGet(() -> MerchantRaw.builder()
                .datasetRowIdx(null)
                .domain(domain)
                .status("observed")
                .ucpUrl("https://" + domain + "/.well-known/ucp")
                .capabilityCount(0)
                .transports("mcp")
                .fetchedAt(now)
                .processed(false)
                .sourceHash(hash(domain))
                .active(true)
                .lastSeenAt(now)
                .build());
        candidate.markForEnrichment(now);
        repository.save(candidate);
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
