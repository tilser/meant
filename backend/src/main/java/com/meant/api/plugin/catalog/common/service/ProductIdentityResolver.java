package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecision;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionOutcome;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionReason;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityContradictionKind;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Small deterministic evidence chain for conservative provider-neutral product reconciliation.
 * Only explicitly typed TRUSTED_EXACT source evidence can merge; titles, free-text attributes,
 * and even high-confidence semantic measurements remain non-merging diagnostics.
 */
final class ProductIdentityResolver {

    private static final Comparator<ProductIdentityEvidence> EVIDENCE_ORDER = Comparator
            .comparing(ProductIdentityEvidence::kind)
            .thenComparing(ProductIdentityEvidence::strength)
            .thenComparing(com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey::evidenceGroupingKey)
            .thenComparingInt(ProductIdentityEvidence::confidenceBasisPoints)
            .thenComparing(evidence -> evidence.sourceReference().type())
            .thenComparing(evidence -> evidence.sourceReference().reference())
            .thenComparing(evidence -> evidence.sourceReference().uri() == null
                    ? ""
                    : evidence.sourceReference().uri().toString());

    private static final Comparator<SignalMatch> MATCH_ORDER = Comparator
            .comparing((SignalMatch match) -> !match.trustedMergeEvidence())
            .thenComparingInt(match -> match.preferred().precedence())
            .thenComparingInt(match -> -match.confidenceBasisPoints())
            .thenComparing(match -> match.preferred().key())
            .thenComparing(match -> match.preferred().kind())
            .thenComparing(SignalMatch::stableEvidenceKey);

    private final ProductIdentitySignalExtractor signalExtractor = new ProductIdentitySignalExtractor();
    private final ProductIdentityCompatibility compatibility = new ProductIdentityCompatibility();
    private final CanonicalProductKeyResolver keyResolver = new CanonicalProductKeyResolver();

    Optional<Resolution> resolve(ProductCandidate left, ProductCandidate right) {
        if (left.offer().key().equals(right.offer().key())) {
            return Optional.of(exact(left, right, ProductGroupingDecisionReason.EXACT_OFFER));
        }
        if (left.fallbackProductKey().equals(right.fallbackProductKey())) {
            return Optional.of(exact(left, right, ProductGroupingDecisionReason.SAME_MERCHANT_PRODUCT));
        }

        Map<String, ProductIdentitySignal> leftSignals = signalExtractor.signals(left).stream()
                .collect(Collectors.toMap(ProductIdentitySignal::key, value -> value, this::preferSignal));
        List<SignalMatch> matches = signalExtractor.signals(right).stream()
                .filter(signal -> leftSignals.containsKey(signal.key()))
                .map(signal -> new SignalMatch(leftSignals.get(signal.key()), signal))
                .filter(match -> scopesAllow(match, left, right))
                .sorted(MATCH_ORDER)
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        SignalMatch match = matches.getFirst();
        List<ProductIdentityContradictionKind> contradictions = contradictions(left, right);
        boolean trusted = match.trustedMergeEvidence();
        boolean semantic = match.preferred().kind() == ProductIdentityEvidenceKind.SEMANTIC;
        boolean authoritativeProviderGroup = match.preferred().kind() == ProductIdentityEvidenceKind.UPID;
        boolean merge = trusted && !semantic && (authoritativeProviderGroup || contradictions.isEmpty());
        ProductGroupingDecisionReason reason = merge
                ? match.preferred().reason()
                : !contradictions.isEmpty() && trusted && !semantic
                        ? ProductGroupingDecisionReason.CONTRADICTION_VETO
                        : semantic
                                ? ProductGroupingDecisionReason.SEMANTIC_EVIDENCE_ONLY
                                : ProductGroupingDecisionReason.LOW_CONFIDENCE_EVIDENCE;
        int confidence = match.confidenceBasisPoints();
        return Optional.of(new Resolution(
                decision(left, right, merge, reason, confidence, match.evidence(), contradictions),
                merge,
                match.preferred().precedence(),
                contradictions
        ));
    }

    List<ProductIdentityContradictionKind> contradictions(ProductCandidate left, ProductCandidate right) {
        return compatibility.contradictions(left, right);
    }

    String canonicalKey(List<ProductCandidate> candidates) {
        return keyResolver.resolve(candidates);
    }

    private Resolution exact(
            ProductCandidate left,
            ProductCandidate right,
            ProductGroupingDecisionReason reason
    ) {
        return new Resolution(
                decision(left, right, true, reason, 10_000, List.of(), List.of()),
                true,
                -1,
                List.of()
        );
    }

    private ProductGroupingDecision decision(
            ProductCandidate left,
            ProductCandidate right,
            boolean merge,
            ProductGroupingDecisionReason reason,
            int confidence,
            List<ProductIdentityEvidence> evidence,
            List<ProductIdentityContradictionKind> contradictions
    ) {
        String first = left.offer().key();
        String second = right.offer().key();
        return new ProductGroupingDecision(
                first.compareTo(second) <= 0 ? first : second,
                first.compareTo(second) <= 0 ? second : first,
                merge ? ProductGroupingDecisionOutcome.GROUPED : ProductGroupingDecisionOutcome.SEPARATE,
                reason,
                confidence,
                evidence,
                contradictions
        );
    }

    private boolean scopesAllow(
            SignalMatch match,
            ProductCandidate left,
            ProductCandidate right
    ) {
        return switch (match.preferred().kind()) {
            case UPID -> left.offer().identity().provider().equals(right.offer().identity().provider())
                    && match.left().evidence().sourceReference().type()
                            == match.right().evidence().sourceReference().type()
                    && match.left().evidence().sourceReference().reference()
                            .equals(match.right().evidence().sourceReference().reference());
            case CANONICAL_URL -> left.offer().identity().merchantScope()
                    .equals(right.offer().identity().merchantScope());
            default -> true;
        };
    }

    private ProductIdentitySignal preferSignal(ProductIdentitySignal first, ProductIdentitySignal second) {
        if (first.trustedMergeEvidence() != second.trustedMergeEvidence()) {
            return first.trustedMergeEvidence() ? first : second;
        }
        return ProductIdentitySignal.ORDER.compare(first, second) <= 0 ? first : second;
    }

    record Resolution(
            ProductGroupingDecision decision,
            boolean merge,
            int precedence,
            List<ProductIdentityContradictionKind> contradictions
    ) {
    }

    private record SignalMatch(ProductIdentitySignal left, ProductIdentitySignal right) {

        private ProductIdentitySignal preferred() {
            return ProductIdentitySignal.ORDER.compare(left, right) <= 0 ? left : right;
        }

        private List<ProductIdentityEvidence> evidence() {
            return java.util.stream.Stream.of(left.evidence(), right.evidence())
                    .distinct()
                    .sorted(EVIDENCE_ORDER)
                    .toList();
        }

        private boolean trustedMergeEvidence() {
            return left.trustedMergeEvidence() && right.trustedMergeEvidence();
        }

        private int confidenceBasisPoints() {
            return Math.min(
                    left.evidence().confidenceBasisPoints(),
                    right.evidence().confidenceBasisPoints()
            );
        }

        private String stableEvidenceKey() {
            return evidence().stream()
                    .map(evidence -> com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey
                            .evidenceGroupingKey(evidence)
                            + ":" + evidence.strength()
                            + ":" + evidence.confidenceBasisPoints()
                            + ":" + evidence.sourceReference().type()
                            + ":" + evidence.sourceReference().reference())
                    .collect(Collectors.joining("|"));
        }
    }
}
