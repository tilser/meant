package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferDelivery;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductCertification;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
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
import org.springframework.stereotype.Service;

/**
 * Groups only identical provider product identities or explicitly trusted exact identity evidence.
 * Semantic and merely asserted evidence is retained but never used to collapse products.
 */
@Service
public class ExactProductGroupingService {

    private static final Comparator<CandidateEntry> CANDIDATE_ORDER = Comparator
            .comparing(
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
            .comparing(ProductIdentityEvidence::kind)
            .thenComparing(ProductIdentityEvidence::strength)
            .thenComparing(CanonicalCommerceKey::evidenceGroupingKey)
            .thenComparingInt(ProductIdentityEvidence::confidenceBasisPoints)
            .thenComparing(evidence -> sourceText(evidence.sourceReference()));

    private static final Comparator<ResultProvenance> PROVENANCE_ORDER = Comparator
            .comparing((ResultProvenance value) -> value.provider().value())
            .thenComparing(value -> value.merchantIntegrationId().toString())
            .thenComparing(value -> identifierText(value.externalMerchantReference()))
            .thenComparing(value -> identifierText(value.externalProductReference()))
            .thenComparing(value -> identifierText(value.externalVariantReference()))
            .thenComparing(value -> value.freshness().observedAt())
            .thenComparing(value -> sourceText(value.sourceReference()));

    public List<CanonicalProduct> group(List<ProductCandidate> sourceCandidates) {
        if (sourceCandidates == null || sourceCandidates.isEmpty()) {
            return List.of();
        }
        List<CandidateEntry> candidates = sourceCandidates.stream()
                .filter(Objects::nonNull)
                .map(CandidateEntry::from)
                .sorted(CANDIDATE_ORDER)
                .toList();
        UnionFind groups = new UnionFind(candidates.size());
        Map<String, Integer> identityOwners = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            for (String groupingIdentity : groupingIdentities(candidates.get(index))) {
                Integer existing = identityOwners.putIfAbsent(groupingIdentity, index);
                if (existing != null) {
                    groups.union(existing, index);
                }
            }
        }

        Map<Integer, List<CandidateEntry>> groupedCandidates = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            groupedCandidates.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>())
                    .add(candidates.get(index));
        }
        return groupedCandidates.values().stream()
                .map(this::assemble)
                .sorted(Comparator.comparing(CanonicalProduct::key))
                .toList();
    }

    private List<String> groupingIdentities(CandidateEntry entry) {
        List<String> identities = new ArrayList<>();
        identities.add("fallback:" + entry.fallbackProductKey());
        entry.candidate().identityEvidence().stream()
                .filter(ProductIdentityEvidence::trustedExact)
                .map(CanonicalCommerceKey::evidenceGroupingKey)
                .sorted()
                .map(value -> "exact:" + value)
                .forEach(identities::add);
        return List.copyOf(identities);
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
                canonicalProductKey(orderedEntries, evidence),
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

    private String canonicalProductKey(
            List<CandidateEntry> candidates,
            List<ProductIdentityEvidence> evidence
    ) {
        return evidence.stream()
                .filter(ProductIdentityEvidence::trustedExact)
                .sorted(EVIDENCE_ORDER)
                .findFirst()
                .map(CanonicalCommerceKey::canonicalProductKey)
                .orElseGet(() -> candidates.stream()
                        .map(CandidateEntry::fallbackProductKey)
                        .min(String::compareTo)
                        .orElseThrow());
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
                distinctSorted(ordered, Offer::selectedOptions, attributeOrder()),
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
                        value.merchantIntegrationId().toString(),
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
