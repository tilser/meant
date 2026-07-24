package com.meant.api.module.merchant.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
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
    private final Cache<ObservationKey, ObservationEntry> observations;

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

    public MerchantUcpProfileObservation observe(
            String domain,
            URI profileEndpoint,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
        ObservationKey key = new ObservationKey(domain, provider, externalMerchantId);
        ObservationEntry entry = observations.get(key, ignored -> new ObservationEntry(fetch(domain, profileEndpoint)));
        enqueueOnce(key, entry);
        return entry.observation();
    }

    public MerchantUcpProfileObservation refresh(
            String domain,
            URI profileEndpoint,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
        ObservationKey key = new ObservationKey(domain, provider, externalMerchantId);
        observations.invalidate(key);
        ObservationEntry entry = new ObservationEntry(fetch(domain, profileEndpoint));
        observations.put(key, entry);
        enqueueOnce(key, entry);
        return entry.observation();
    }

    private MerchantUcpProfileObservation fetch(String domain, URI profileEndpoint) {
        UcpProfileFetchResult result = profileClient.fetchProfileResult(domain, profileEndpoint.toString());
        UcpProfile profile = result.profile();
        return new MerchantUcpProfileObservation(
                domain,
                profileEndpoint,
                profile,
                profile.services() == null ? Map.of() : Map.copyOf(profile.services()),
                profile.capabilities() == null ? Set.of() : Set.copyOf(profile.capabilities().keySet()),
                result.capturedAt()
        );
    }

    private void enqueueOnce(ObservationKey key, ObservationEntry entry) {
        if (!entry.enrichmentQueued().compareAndSet(false, true)) {
            return;
        }
        try {
            enrichmentCandidates.enqueue(key.domain(), key.provider(), key.externalMerchantId());
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

    private record ObservationKey(
            String domain,
            MerchantIntegrationProvider provider,
            String externalMerchantId
    ) {
    }
}
