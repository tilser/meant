package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferDelivery;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductCertification;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecision;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionOutcome;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionReason;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingResult;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityContradictionKind;
import com.meant.api.plugin.catalog.common.dto.ProductMaterial;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deduplicates exact offers, then groups only provider products or cross-source evidence accepted
 * by the conservative identity resolver. Semantic and asserted evidence remains measurable only.
 */
@Service
public class ExactProductGroupingService {

    private final ProductIdentityResolver identityResolver = new ProductIdentityResolver();
    private final ProductGroupingMetrics metrics;

    private static final Comparator<CandidateEntry> CANDIDATE_ORDER = Comparator
            .comparingInt((CandidateEntry entry) -> evidencePreference(entry.candidate()))
            .thenComparing(
                    (CandidateEntry entry) -> latestObservation(entry.candidate()),
                    Comparator.reverseOrder()
            )
            .thenComparing(CandidateEntry::fallbackProductKey)
            .thenComparing(CandidateEntry::offerKey)
            .thenComparing(entry -> nullToEmpty(entry.candidate().title()))
            .thenComparing(entry -> nullToEmpty(entry.candidate().description()))
            .thenComparing(entry -> candidateProvenanceText(entry.candidate()))
            .thenComparing(entry -> offerContentText(entry.candidate().offer()));

    private static final Comparator<ProductIdentityEvidence> EVIDENCE_ORDER = Comparator
            .comparingInt((ProductIdentityEvidence evidence) -> evidencePreference(evidence))
            .thenComparing(ProductIdentityEvidence::kind)
            .thenComparing(ProductIdentityEvidence::strength)
            .thenComparing(CanonicalCommerceKey::evidenceGroupingKey)
            .thenComparingInt(ProductIdentityEvidence::confidenceBasisPoints)
            .thenComparing(evidence -> sourceText(evidence.sourceReference()));

    private static final Comparator<ResultProvenance> PROVENANCE_ORDER = Comparator
            .comparing((ResultProvenance value) -> value.provider().value())
            .thenComparing(value -> value.discoverySource().type())
            .thenComparing(value -> value.discoverySource().value())
            .thenComparing(value -> routingText(value.localRouting()))
            .thenComparing(value -> identifierText(value.externalMerchantReference()))
            .thenComparing(value -> identifierText(value.externalProductReference()))
            .thenComparing(value -> identifierText(value.externalVariantReference()))
            .thenComparing(value -> value.freshness().observedAt())
            .thenComparing(value -> sourceText(value.sourceReference()));

    public ExactProductGroupingService() {
        this(ProductGroupingMetrics.noop());
    }

    @Autowired
    public ExactProductGroupingService(ProductGroupingMetrics metrics) {
        this.metrics = metrics;
    }

    public List<CanonicalProduct> group(List<ProductCandidate> sourceCandidates) {
        return evaluate(sourceCandidates).products();
    }

    public ProductGroupingResult evaluate(List<ProductCandidate> sourceCandidates) {
        if (sourceCandidates == null || sourceCandidates.isEmpty()) {
            return new ProductGroupingResult(List.of(), List.of());
        }
        List<CandidateEntry> candidates = sourceCandidates.stream()
                .filter(Objects::nonNull)
                .map(CandidateEntry::from)
                .sorted(CANDIDATE_ORDER)
                .toList();
        UnionFind groups = new UnionFind(candidates.size());
        List<GroupingEdge> edges = new ArrayList<>();
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                int first = left;
                int second = right;
                identityResolver.resolve(candidates.get(first).candidate(), candidates.get(second).candidate())
                        .ifPresent(resolution -> edges.add(new GroupingEdge(first, second, resolution)));
            }
        }
        edges.sort(Comparator.comparingInt((GroupingEdge edge) -> edge.resolution().precedence())
                .thenComparing(edge -> edge.resolution().decision().leftOfferKey())
                .thenComparing(edge -> edge.resolution().decision().rightOfferKey()));
        List<ProductGroupingDecision> decisions = new ArrayList<>();
        for (GroupingEdge edge : edges) {
            ProductIdentityResolver.Resolution resolution = edge.resolution();
            if (!resolution.merge() || groups.find(edge.left()) == groups.find(edge.right())) {
                decisions.add(resolution.decision());
                continue;
            }
            List<ProductIdentityContradictionKind> transitiveContradictions = bypassProductContradictions(resolution)
                    ? List.of()
                    : clusterContradictions(groups, edge.left(), edge.right(), candidates, identityResolver);
            if (transitiveContradictions.isEmpty()) {
                groups.union(edge.left(), edge.right());
                decisions.add(resolution.decision());
            } else {
                decisions.add(transitiveVeto(resolution.decision(), transitiveContradictions));
            }
        }

        Map<Integer, List<CandidateEntry>> groupedCandidates = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            groupedCandidates.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>())
                    .add(candidates.get(index));
        }
        List<CanonicalProduct> products = groupedCandidates.values().stream()
                .map(this::assemble)
                .sorted(Comparator.comparing(CanonicalProduct::key))
                .toList();
        List<ProductGroupingDecision> orderedDecisions = decisions.stream()
                .distinct()
                .sorted(Comparator.comparing(ProductGroupingDecision::leftOfferKey)
                        .thenComparing(ProductGroupingDecision::rightOfferKey)
                        .thenComparing(ProductGroupingDecision::reason))
                .toList();
        metrics.record(orderedDecisions);
        return new ProductGroupingResult(products, orderedDecisions);
    }

    private CanonicalProduct assemble(List<CandidateEntry> candidates) {
        List<CandidateEntry> orderedEntries = candidates.stream().sorted(CANDIDATE_ORDER).toList();
        List<ProductCandidate> ordered = orderedEntries.stream().map(CandidateEntry::candidate).toList();
        List<ProductIdentityEvidence> evidence = distinctSorted(
                ordered,
                ProductCandidate::identityEvidence,
                EVIDENCE_ORDER
        );
        List<ResultProvenance> provenance = distinctSorted(
                ordered,
                ProductCandidate::provenance,
                PROVENANCE_ORDER
        );
        return new CanonicalProduct(
                canonicalProductKey(orderedEntries),
                firstText(ordered, ProductCandidate::title),
                firstText(ordered, ProductCandidate::description),
                distinctSorted(ordered, ProductCandidate::media, mediaOrder()),
                distinctSorted(ordered, ProductCandidate::attributes, attributeOrder()),
                distinctSorted(ordered, ProductCandidate::materials, materialOrder()),
                distinctSorted(ordered, ProductCandidate::certifications, certificationOrder()),
                distinctSorted(ordered, ProductCandidate::attribution, attributionOrder()),
                evidence,
                provenance,
                assembleOffers(orderedEntries)
        );
    }

    private String canonicalProductKey(List<CandidateEntry> candidates) {
        return identityResolver.canonicalKey(
                candidates.stream().map(CandidateEntry::candidate).toList());
    }

    private List<ProductIdentityContradictionKind> clusterContradictions(
            UnionFind groups,
            int left,
            int right,
            List<CandidateEntry> candidates,
            ProductIdentityResolver resolver
    ) {
        LinkedHashSet<ProductIdentityContradictionKind> contradictions = new LinkedHashSet<>();
        int leftRoot = groups.find(left);
        int rightRoot = groups.find(right);
        for (int first = 0; first < candidates.size(); first++) {
            if (groups.find(first) != leftRoot) {
                continue;
            }
            for (int second = 0; second < candidates.size(); second++) {
                if (groups.find(second) == rightRoot) {
                    contradictions.addAll(resolver.contradictions(
                            candidates.get(first).candidate(),
                            candidates.get(second).candidate()
                    ));
                }
            }
        }
        return contradictions.stream().sorted().toList();
    }

    private boolean bypassProductContradictions(ProductIdentityResolver.Resolution resolution) {
        return resolution.decision().reason() == ProductGroupingDecisionReason.EXACT_OFFER
                || resolution.decision().reason() == ProductGroupingDecisionReason.SAME_MERCHANT_PRODUCT
                || resolution.decision().reason() == ProductGroupingDecisionReason.TRUSTED_PROVIDER_GROUP;
    }

    private ProductGroupingDecision transitiveVeto(
            ProductGroupingDecision decision,
            List<ProductIdentityContradictionKind> contradictions
    ) {
        return new ProductGroupingDecision(
                decision.leftOfferKey(),
                decision.rightOfferKey(),
                ProductGroupingDecisionOutcome.SEPARATE,
                ProductGroupingDecisionReason.TRANSITIVE_CONTRADICTION_VETO,
                decision.confidenceBasisPoints(),
                decision.evidence(),
                contradictions
        );
    }

    private List<Offer> assembleOffers(List<CandidateEntry> candidates) {
        Map<String, List<Offer>> grouped = new LinkedHashMap<>();
        candidates.forEach(entry -> grouped.computeIfAbsent(entry.offerKey(), ignored -> new ArrayList<>())
                .add(entry.candidate().offer()));
        return grouped.values().stream()
                .map(this::mergeOfferObservations)
                .sorted(Comparator.comparing(Offer::key))
                .toList();
    }

    private Offer mergeOfferObservations(List<Offer> observations) {
        List<Offer> ordered = observations.stream()
                .sorted(Comparator.comparing(
                                (Offer offer) -> latestObservation(offer),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(offer -> nullToEmpty(offer.merchantName()))
                        .thenComparing(offer -> nullToEmpty(offer.variantTitle()))
                        .thenComparing(ExactProductGroupingService::offerProvenanceText)
                        .thenComparing(ExactProductGroupingService::offerContentText))
                .toList();
        Offer preferred = ordered.getFirst();
        return new Offer(
                preferred.identity(),
                firstText(ordered, Offer::merchantName),
                firstText(ordered, Offer::variantTitle),
                firstNonNull(ordered, Offer::price),
                firstNonNull(ordered, Offer::listPrice),
                preferred.availability(),
                distinctSorted(ordered, Offer::delivery, deliveryOrder()),
                firstNonNull(ordered, Offer::checkoutUrl),
                distinctSorted(ordered, Offer::provenance, PROVENANCE_ORDER)
        );
    }

    private static <S, T> List<T> distinctSorted(
            List<S> sources,
            Function<S, List<T>> values,
            Comparator<T> comparator
    ) {
        LinkedHashSet<T> distinct = new LinkedHashSet<>();
        for (S source : sources) {
            distinct.addAll(values.apply(source));
        }
        return distinct.stream().sorted(comparator).toList();
    }

    private static <S> String firstText(List<S> sources, Function<S, String> value) {
        return sources.stream()
                .map(value)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static <S, T> T firstNonNull(List<S> sources, Function<S, T> value) {
        return sources.stream().map(value).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static Instant latestObservation(ProductCandidate candidate) {
        return candidate.provenance().stream()
                .map(value -> value.freshness().observedAt())
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
    }

    private static Instant latestObservation(Offer offer) {
        return offer.provenance().stream()
                .map(value -> value.freshness().observedAt())
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
    }

    private static int evidencePreference(ProductCandidate candidate) {
        return candidate.identityEvidence().stream()
                .mapToInt(ExactProductGroupingService::evidencePreference)
                .min()
                .orElse(100);
    }

    private static int evidencePreference(ProductIdentityEvidence evidence) {
        int kind = switch (evidence.kind()) {
            case UPID -> 0;
            case GTIN, UPC, EAN, UNIVERSAL_PRODUCT_ID -> 1;
            case BRAND_MPN -> 2;
            case PROVIDER_GROUPING_ID -> 3;
            case CANONICAL_URL -> 4;
            case SEMANTIC -> 5;
        };
        return evidence.trustedExact() ? kind : kind + 10;
    }

    private static Comparator<ProductMedia> mediaOrder() {
        return Comparator.comparing(ProductMedia::type)
                .thenComparing(value -> value.url().toString())
                .thenComparing(value -> nullToEmpty(value.altText()));
    }

    private static Comparator<ProductAttribute> attributeOrder() {
        return Comparator.comparing((ProductAttribute value) -> nullToEmpty(value.group()))
                .thenComparing(ProductAttribute::name)
                .thenComparing(ProductAttribute::value);
    }

    private static Comparator<ProductMaterial> materialOrder() {
        return Comparator.comparing(ProductMaterial::name)
                .thenComparing(value -> value.percentageBasisPoints() == null ? -1 : value.percentageBasisPoints());
    }

    private static Comparator<ProductCertification> certificationOrder() {
        return Comparator.comparing(ProductCertification::name)
                .thenComparing(value -> nullToEmpty(value.issuer()))
                .thenComparing(value -> nullToEmpty(value.identifier()));
    }

    private static Comparator<ProductAttribution> attributionOrder() {
        return Comparator.comparing(ProductAttribution::label)
                .thenComparing(value -> value.url() == null ? "" : value.url().toString())
                .thenComparing(value -> sourceText(value.sourceReference()));
    }

    private static Comparator<OfferDelivery> deliveryOrder() {
        return Comparator.comparing(OfferDelivery::method)
                .thenComparing(value -> nullToEmpty(value.destinationRegion()))
                .thenComparing(value -> value.minimumBusinessDays() == null ? -1 : value.minimumBusinessDays())
                .thenComparing(value -> value.maximumBusinessDays() == null ? -1 : value.maximumBusinessDays());
    }

    private static String candidateProvenanceText(ProductCandidate candidate) {
        return provenanceText(candidate.provenance());
    }

    private static String offerProvenanceText(Offer offer) {
        return provenanceText(offer.provenance());
    }

    private static String provenanceText(List<ResultProvenance> provenance) {
        return provenance.stream()
                .sorted(PROVENANCE_ORDER)
                .map(value -> encodedText(
                        value.provider().value(),
                        value.discoverySource().type().name(),
                        value.discoverySource().value(),
                        routingText(value.localRouting()),
                        identifierText(value.externalMerchantReference()),
                        identifierText(value.externalProductReference()),
                        identifierText(value.externalVariantReference()),
                        value.freshness().observedAt().toString(),
                        value.freshness().freshUntil() == null ? null : value.freshness().freshUntil().toString(),
                        sourceText(value.sourceReference())
                ))
                .collect(Collectors.joining());
    }

    private static String offerContentText(Offer offer) {
        return encodedText(
                offer.price() == null ? null : Long.toString(offer.price().minorUnits()),
                offer.price() == null ? null : offer.price().currency(),
                offer.listPrice() == null ? null : Long.toString(offer.listPrice().minorUnits()),
                offer.listPrice() == null ? null : offer.listPrice().currency(),
                offer.availability().status().name(),
                offer.availability().quantity() == null ? null : offer.availability().quantity().toString(),
                offer.availability().availableAt() == null ? null : offer.availability().availableAt().toString(),
                offer.checkoutUrl() == null ? null : offer.checkoutUrl().toString()
        );
    }

    private static String identifierText(ExternalIdentifier identifier) {
        if (identifier == null) {
            return "";
        }
        return encodedText(identifier.type().name(), identifier.namespace(), identifier.value());
    }

    private static String routingText(LocalMerchantRouting routing) {
        return routing == null ? "" : routing.merchantIntegrationId().toString();
    }

    private static String sourceText(com.meant.api.plugin.catalog.common.dto.ResultSourceReference source) {
        return encodedText(
                source.type().name(),
                source.reference(),
                source.uri() == null ? null : source.uri().toString()
        );
    }

    private static String encodedText(String... values) {
        StringBuilder encoded = new StringBuilder();
        for (String value : values) {
            encoded.append(value == null ? -1 : value.length()).append(':');
            if (value != null) {
                encoded.append(value);
            }
        }
        return encoded.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CandidateEntry(
            ProductCandidate candidate,
            String fallbackProductKey,
            String offerKey
    ) {

        private static CandidateEntry from(ProductCandidate candidate) {
            return new CandidateEntry(
                    candidate,
                    candidate.fallbackProductKey(),
                    candidate.offer().key()
            );
        }
    }

    private record GroupingEdge(
            int left,
            int right,
            ProductIdentityResolver.Resolution resolution
    ) {
    }

    private static final class UnionFind {

        private final int[] parent;

        private UnionFind(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot != secondRoot) {
                parent[Math.max(firstRoot, secondRoot)] = Math.min(firstRoot, secondRoot);
            }
        }
    }
}
