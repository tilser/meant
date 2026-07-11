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
    private final ProductIdentityCompatibility compatibility = new ProductIdentityCompatibility();

    String resolve(List<ProductCandidate> candidates) {
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
                .filter(value -> value.candidateIndexes().size() == candidates.size())
                .filter(value -> scopeAllowed(value.signal(), candidates, value.candidateIndexes()))
                .sorted(Comparator.comparing(SignalCoverage::signal, ProductIdentitySignal.ORDER)
                        .thenComparing(value -> value.signal().key()))
                .map(SignalCoverage::signal)
                .findFirst()
                .map(signal -> productKey(signal, candidates))
                .orElseGet(() -> CanonicalCommerceKey.clusteredProductKey(candidates.stream()
                        .map(ProductCandidate::fallbackProductKey)
                        .toList()));
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
            return matching.stream().map(candidate -> candidate.offer().identity().provider()).distinct().count() == 1
                    && matching.stream()
                            .flatMap(candidate -> signalExtractor.signals(candidate).stream())
                            .filter(candidateSignal -> candidateSignal.key().equals(signal.key()))
                            .map(candidateSignal -> candidateSignal.evidence().sourceReference().type()
                                    + ":" + candidateSignal.evidence().sourceReference().reference())
                            .distinct()
                            .count() == 1;
        }
        return true;
    }

    private String productKey(ProductIdentitySignal signal, List<ProductCandidate> candidates) {
        String compatibilityFingerprint = signal.kind() == ProductIdentityEvidenceKind.UPID
                ? "authoritative-provider-group"
                : compatibility.fingerprint(candidates);
        return CanonicalCommerceKey.groupedProductKey(
                signal.reason().name(),
                signal.key(),
                compatibilityFingerprint
        );
    }

    private record SignalCoverage(ProductIdentitySignal signal, Set<Integer> candidateIndexes) {

        private SignalCoverage(ProductIdentitySignal signal) {
            this(signal, new HashSet<>());
        }
    }
}
