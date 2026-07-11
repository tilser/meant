package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.CatalogSearchRetentionAdmission;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/** Single fail-closed resolution boundary used by modules before provider data crosses into storage. */
@Service
public class CatalogDataUsePolicyResolver {
    static final String UNKNOWN_POLICY = "unknown-source-v1";

    private final List<CatalogDataUsePolicy> policies;
    private final CatalogDataUsePolicyMetrics metrics;

    public CatalogDataUsePolicyResolver(List<CatalogDataUsePolicy> policies, CatalogDataUsePolicyMetrics metrics) {
        this.policies = List.copyOf(policies);
        this.metrics = metrics;
    }

    public CatalogRetentionDecision resolve(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass) {
        List<CatalogDataUsePolicy> matching = source == null
                ? List.of()
                : policies.stream().filter(policy -> policy.supports(source)).toList();
        CatalogRetentionDecision decision;
        String resolution;
        if (matching.size() != 1) {
            decision = CatalogRetentionDecision.sessionOnly(UNKNOWN_POLICY);
            resolution = matching.isEmpty() ? "unknown" : "ambiguous";
        } else {
            decision = matching.getFirst().decide(source, payloadClass);
            resolution = "explicit";
        }
        metrics.record(payloadClass, decision, resolution);
        return decision;
    }

    public CatalogSearchRetentionAdmission admitSearch(Collection<DiscoverySourceIdentity> sources) {
        if (sources == null || sources.isEmpty() || sources.stream().anyMatch(java.util.Objects::isNull)) {
            return CatalogSearchRetentionAdmission.rejected(CatalogRetentionMode.SESSION_ONLY);
        }
        List<DecisionKey> decisions = sources.stream()
                .distinct()
                .sorted(Comparator.comparing(this::sourceKey))
                .flatMap(source -> java.util.stream.Stream.of(
                        decision(source, CatalogPayloadClass.SEARCH_FACTS),
                        decision(source, CatalogPayloadClass.SEARCH_MEDIA)
                ))
                .toList();
        DecisionKey rejection = decisions.stream()
                .filter(decision -> decision.decision().mode() != CatalogRetentionMode.BOUNDED_CACHE)
                .findFirst()
                .orElse(null);
        if (rejection != null) {
            return CatalogSearchRetentionAdmission.rejected(rejection.decision().mode());
        }
        Duration retention = decisions.stream()
                .map(decision -> decision.decision().maximumRetention())
                .min(Duration::compareTo)
                .orElseThrow();
        String material = decisions.stream()
                .map(DecisionKey::fingerprintPart)
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        return CatalogSearchRetentionAdmission.admitted(retention, sha256(material));
    }

    private DecisionKey decision(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass) {
        return new DecisionKey(source, payloadClass, resolve(source, payloadClass));
    }

    private String sourceKey(DiscoverySourceIdentity source) {
        return source.provider().value() + "|" + source.type().name() + "|" + source.value();
    }

    private String sha256(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private record DecisionKey(
            DiscoverySourceIdentity source,
            CatalogPayloadClass payloadClass,
            CatalogRetentionDecision decision
    ) {
        String fingerprintPart() {
            return source.provider().value() + "|" + source.type().name() + "|" + source.value()
                    + "|" + payloadClass.name() + "|" + decision.policyKey() + "|"
                    + decision.mode().name() + "|" + decision.maximumRetention();
        }
    }
}
