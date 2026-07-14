package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.port.CatalogProductDetailProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Dispatches a single current get-product call without widening batch/cart rehydration. */
@Service
@RequiredArgsConstructor
public class CatalogProductDetailService {
    private final List<CatalogProductDetailProvider> providers;
    private final CatalogProductRehydrationMetrics metrics;

    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        List<CatalogProductDetailProvider> matching = providers.stream()
                .filter(provider -> provider.supportsDetails(reference.discoverySource()))
                .toList();
        if (matching.size() != 1) {
            CatalogProductDetailResult failed = CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.UNSUPPORTED,
                    matching.isEmpty()
                            ? CatalogRehydrationFailureKind.NO_PROVIDER
                            : CatalogRehydrationFailureKind.AMBIGUOUS_PROVIDER
            );
            metrics.record(failed.rehydration());
            return failed;
        }
        try {
            CatalogProductDetailResult result = matching.getFirst().getDetails(reference, context);
            if (result == null || !reference.equals(result.rehydration().reference())) {
                result = CatalogProductDetailResult.failed(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.INVALID_RESPONSE
                );
            }
            metrics.record(result.rehydration());
            return result;
        } catch (RuntimeException exception) {
            CatalogProductDetailResult failed = CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
            metrics.record(failed.rehydration());
            return failed;
        }
    }
}
