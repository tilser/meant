package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects a stable canonical key from evidence that actually covers the assembled product cluster. */
final class CanonicalProductKeyResolver {

    private final ProductIdentitySignalExtractor signalExtractor = new ProductIdentitySignalExtractor();

    String resolve(List<ProductCandidate> candidates) {
        boolean oneExactOffer = candidates.stream().map(candidate -> candidate.offer().key()).distinct().count() == 1;
        if (candidates.size() == 1 || oneExactOffer) {
            return candidates.stream()
                    .flatMap(candidate -> signalExtractor.signals(candidate).stream())
                    .filter(ProductIdentitySignal::trustedMergeEvidence)
                    .sorted(ProductIdentitySignal.ORDER)
                    .findFirst()
                    .map(this::productKey)
                    .orElseGet(candidates.getFirst()::fallbackProductKey);
        }
        Map<String, SignalCoverage> coverage = new HashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            for (ProductIdentitySignal signal : signalExtractor.signals(candidates.get(index))) {
                if (signal.trustedMergeEvidence()) {
                    coverage.computeIfAbsent(signal.key(), ignored -> new SignalCoverage(signal))
                            .candidateIndexes().add(index);
                }
            }
        }
        return coverage.values().stream()
                .filter(value -> value.candidateIndexes().size() >= 2)
                .filter(value -> scopeAllowed(value.signal(), candidates, value.candidateIndexes()))
                .sorted(Comparator.comparing(SignalCoverage::signal, ProductIdentitySignal.ORDER)
                        .thenComparingInt(value -> -value.candidateIndexes().size()))
                .map(SignalCoverage::signal)
                .findFirst()
                .map(this::productKey)
                .orElseGet(() -> candidates.stream()
                        .map(ProductCandidate::fallbackProductKey)
                        .min(String::compareTo)
                        .orElseThrow());
    }

    private boolean scopeAllowed(
            ProductIdentitySignal signal,
            List<ProductCandidate> candidates,
            Set<Integer> indexes
    ) {
        List<ProductCandidate> matching = indexes.stream().sorted().map(candidates::get).toList();
        if (signal.kind() == ProductIdentityEvidenceKind.CANONICAL_URL) {
            return matching.stream().map(candidate -> candidate.offer().identity().merchantScope()).distinct().count() == 1;
        }
        if (signal.kind() == ProductIdentityEvidenceKind.UPID) {
            return matching.stream().map(candidate -> candidate.offer().identity().provider()).distinct().count() == 1;
        }
        return true;
    }

    private String productKey(ProductIdentitySignal signal) {
        return CanonicalCommerceKey.groupedProductKey(signal.reason().name(), signal.key());
    }

    private record SignalCoverage(ProductIdentitySignal signal, Set<Integer> candidateIndexes) {

        private SignalCoverage(ProductIdentitySignal signal) {
            this(signal, new HashSet<>());
        }
    }
}
