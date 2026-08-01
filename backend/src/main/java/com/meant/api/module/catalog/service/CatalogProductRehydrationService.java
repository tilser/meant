package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.catalog.service.support.CatalogProductObservationCache;
import com.meant.api.module.catalog.service.support.PreferredCurrencyPriceNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Provider-neutral, batched rehydration dispatch over the existing provider adapters. */
@Service
public class CatalogProductRehydrationService {
    private final List<CatalogProductRehydrationProvider> providers;
    private final CatalogProductRehydrationMetrics metrics;
    private final CatalogProductObservationCache observationCache;

    @Autowired
    public CatalogProductRehydrationService(
            List<CatalogProductRehydrationProvider> providers,
            CatalogProductRehydrationMetrics metrics,
            CatalogProductObservationCache observationCache
    ) {
        this.providers = List.copyOf(providers);
        this.metrics = metrics;
        this.observationCache = observationCache;
    }

    public CatalogProductRehydrationService(
            List<CatalogProductRehydrationProvider> providers,
            CatalogProductRehydrationMetrics metrics
    ) {
        this(providers, metrics, CatalogProductObservationCache.withDefaults());
    }

    public CatalogProductRehydrationResult rehydrate(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        return rehydrate(List.of(reference), context).getFirst();
    }

    public List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    ) {
        Map<CatalogProductRehydrationProvider, List<CatalogProductReference>> batches = new LinkedHashMap<>();
        Map<CatalogProductReference, CatalogProductRehydrationResult> results = new LinkedHashMap<>();
        for (CatalogProductReference reference : references) {
            CatalogProductRehydrationResult cached = observationCache.findRehydration(reference, context)
                    .orElse(null);
            if (cached != null) {
                results.put(reference, cached);
                metrics.record(cached);
                continue;
            }
            List<CatalogProductRehydrationProvider> matching = providers.stream()
                    .filter(provider -> provider.supports(reference.discoverySource()))
                    .toList();
            if (matching.size() != 1) {
                CatalogProductRehydrationResult failure = CatalogProductRehydrationResult.failed(
                        reference,
                        CatalogRehydrationStatus.UNSUPPORTED,
                        matching.isEmpty()
                                ? CatalogRehydrationFailureKind.NO_PROVIDER
                                : CatalogRehydrationFailureKind.AMBIGUOUS_PROVIDER
                );
                results.put(reference, failure);
                metrics.record(failure);
            } else {
                batches.computeIfAbsent(matching.getFirst(), ignored -> new ArrayList<>()).add(reference);
            }
        }
        batches.forEach((provider, batch) -> {
            try {
                List<CatalogProductRehydrationResult> providerResults = provider.rehydrate(List.copyOf(batch), context);
                if (providerResults != null) {
                    for (CatalogProductRehydrationResult result : providerResults) {
                        if (result != null && batch.contains(result.reference())) {
                            result = PreferredCurrencyPriceNormalizer.normalize(result, context);
                            results.put(result.reference(), result);
                            observationCache.rememberRehydration(result, context);
                            metrics.record(result);
                        }
                    }
                }
            } catch (RuntimeException exception) {
                for (CatalogProductReference reference : batch) {
                    CatalogProductRehydrationResult failure = CatalogProductRehydrationResult.failed(
                            reference,
                            CatalogRehydrationStatus.DEGRADED,
                            CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                    );
                    results.put(reference, failure);
                    metrics.record(failure);
                }
            }
            for (CatalogProductReference reference : batch) {
                results.computeIfAbsent(reference, ignored -> {
                    CatalogProductRehydrationResult missing = CatalogProductRehydrationResult.failed(
                            reference,
                            CatalogRehydrationStatus.DEGRADED,
                            CatalogRehydrationFailureKind.INVALID_RESPONSE
                    );
                    metrics.record(missing);
                    return missing;
                });
            }
        });
        return references.stream().map(results::get).toList();
    }
}
