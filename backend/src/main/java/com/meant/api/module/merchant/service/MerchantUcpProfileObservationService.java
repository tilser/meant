package com.meant.api.module.merchant.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.merchant.properties.MerchantUcpProfileObservationProperties;
import com.meant.api.module.merchant.service.dto.MerchantUcpProfileObservation;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Provider-neutral bounded cache for executable merchant profile routing observations. */
@Service
public class MerchantUcpProfileObservationService {
    private final UcpProfileClient profileClient;
    private final MerchantEnrichmentCandidateService enrichmentCandidates;
    private final Cache<String, ObservationEntry> observations;

    @Autowired
    public MerchantUcpProfileObservationService(
            UcpProfileClient profileClient,
            MerchantEnrichmentCandidateService enrichmentCandidates,
            MerchantUcpProfileObservationProperties properties
    ) {
        this.profileClient = profileClient;
        this.enrichmentCandidates = enrichmentCandidates;
        this.observations = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.ttl())
                .build();
    }

    public MerchantUcpProfileObservation observe(String domain, URI profileEndpoint) {
        ObservationEntry entry = observations.get(domain, ignored -> new ObservationEntry(fetch(domain, profileEndpoint)));
        enqueueOnce(domain, entry);
        return entry.observation();
    }

    public MerchantUcpProfileObservation refresh(String domain, URI profileEndpoint) {
        observations.invalidate(domain);
        ObservationEntry entry = new ObservationEntry(fetch(domain, profileEndpoint));
        observations.put(domain, entry);
        enqueueOnce(domain, entry);
        return entry.observation();
    }

    private MerchantUcpProfileObservation fetch(String domain, URI profileEndpoint) {
        UcpProfileFetchResult result = profileClient.fetchProfileResult(domain, profileEndpoint.toString());
        UcpProfile profile = result.profile();
        return new MerchantUcpProfileObservation(
                domain,
                profileEndpoint,
                profile.services() == null ? Map.of() : Map.copyOf(profile.services()),
                profile.capabilities() == null ? Set.of() : Set.copyOf(profile.capabilities().keySet()),
                result.capturedAt()
        );
    }

    private void enqueueOnce(String domain, ObservationEntry entry) {
        if (!entry.enrichmentQueued().compareAndSet(false, true)) {
            return;
        }
        try {
            enrichmentCandidates.enqueue(domain);
        } catch (RuntimeException exception) {
            entry.enrichmentQueued().set(false);
            throw exception;
        }
    }

    private record ObservationEntry(
            MerchantUcpProfileObservation observation,
            AtomicBoolean enrichmentQueued
    ) {
        private ObservationEntry(MerchantUcpProfileObservation observation) {
            this(observation, new AtomicBoolean());
        }
    }
}
