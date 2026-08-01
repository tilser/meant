package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Fail-closed exact lookup gate between Shopify search results and buyer-visible products. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShopifyCatalogSearchCandidateVerifier {
    private static final int DIAGNOSTIC_SAMPLE_LIMIT = 5;

    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties properties;
    private final ShopifyCatalogReferenceMatcher matcher;

    public Verification verify(
            List<ProductCandidate> candidates,
            ShopifyCatalogContext context,
            ShopifyCatalogFilters searchFilters
    ) {
        List<ProductCandidate> requested = candidates == null
                ? List.of()
                : candidates.stream()
                        .filter(Objects::nonNull)
                        .limit(ShopifyGlobalCatalogProperties.MAXIMUM_VERIFIED_CANDIDATES)
                        .toList();
        if (requested.isEmpty()) {
            return Verification.success(List.of(), candidates != null && !candidates.isEmpty());
        }

        List<CandidateReference> references = requested.stream()
                .map(this::reference)
                .filter(Objects::nonNull)
                .toList();
        List<String> variantIds = references.stream()
                .map(CandidateReference::reference)
                .map(CatalogProductReference::externalVariantReference)
                .filter(Objects::nonNull)
                .map(ExternalIdentifier::value)
                .distinct()
                .toList();
        List<ProductCandidate> resolved = new ArrayList<>();
        for (int start = 0; start < variantIds.size(); start += properties.maximumLookupIds()) {
            List<String> batch = variantIds.subList(
                    start,
                    Math.min(start + properties.maximumLookupIds(), variantIds.size())
            );
            var lookup = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(
                    batch,
                    context,
                    verificationFilters(searchFilters)
            ));
            if (lookup == null) {
                return Verification.failed(new CatalogSourceFailure(
                        CatalogSourceFailureKind.MALFORMED_RESPONSE,
                        "Shopify Global Catalog lookup verification returned no result",
                        null,
                        null
                ));
            }
            if (!lookup.successful()) {
                return Verification.failed(lookup.failure());
            }
            resolved.addAll(lookup.candidates());
        }

        List<ProductCandidate> verified = references.stream()
                .filter(candidate -> matcher.match(candidate.reference(), resolved) != null)
                .map(CandidateReference::candidate)
                .toList();
        int excluded = candidates == null ? 0 : candidates.size() - verified.size();
        if (excluded > 0) {
            log.warn(
                    "Shopify search candidates excluded before presentation because exact lookup could not verify "
                            + "them; candidateCount={}, verifiedCount={}, excludedCount={}, merchantDomains={}",
                    candidates.size(),
                    verified.size(),
                    excluded,
                    diagnosticMerchantDomains(candidates, verified)
            );
        }
        return Verification.success(verified, candidates != null && candidates.size() > requested.size());
    }

    private CandidateReference reference(ProductCandidate candidate) {
        Offer offer = candidate.offer();
        if (offer == null || offer.identity() == null || offer.identity().externalVariantIdentity() == null) {
            return null;
        }
        return offer.provenance().stream()
                .filter(Objects::nonNull)
                .map(provenance -> reference(candidate, offer, provenance))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private CandidateReference reference(
            ProductCandidate candidate,
            Offer offer,
            ResultProvenance provenance
    ) {
        ExternalIdentifier merchant = offer.identity().merchantScope().externalMerchantIdentity();
        if (merchant == null) {
            merchant = provenance.externalMerchantReference();
        }
        if (merchant == null || provenance.externalProductReference() == null
                || provenance.externalVariantReference() == null) {
            return null;
        }
        try {
            return new CandidateReference(candidate, new CatalogProductReference(
                    offer.key(),
                    provenance.discoverySource(),
                    null,
                    provenance.localRouting(),
                    merchant,
                    provenance.externalMerchantDomain(),
                    provenance.externalProductReference(),
                    provenance.externalVariantReference(),
                    offer.selectedOptions(),
                    offer.identity().components(),
                    offer.identity().sellingPlanIdentity()
            ));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ShopifyCatalogFilters verificationFilters(ShopifyCatalogFilters filters) {
        if (filters == null) {
            return null;
        }
        return new ShopifyCatalogFilters(
                filters.available(),
                filters.condition(),
                filters.shipsTo(),
                filters.shipsFrom(),
                null,
                filters.shops(),
                null,
                null,
                null,
                null
        );
    }

    private List<String> diagnosticMerchantDomains(
            List<ProductCandidate> requested,
            List<ProductCandidate> verified
    ) {
        LinkedHashSet<ProductCandidate> accepted = new LinkedHashSet<>(verified);
        return requested.stream()
                .filter(candidate -> !accepted.contains(candidate))
                .flatMap(candidate -> candidate.offer().provenance().stream())
                .map(ResultProvenance::externalMerchantDomain)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(DIAGNOSTIC_SAMPLE_LIMIT)
                .toList();
    }

    public record Verification(
            List<ProductCandidate> candidates,
            boolean truncated,
            CatalogSourceFailure failure
    ) {
        public Verification {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        static Verification success(List<ProductCandidate> candidates, boolean truncated) {
            return new Verification(candidates, truncated, null);
        }

        static Verification failed(CatalogSourceFailure failure) {
            return new Verification(List.of(), false, failure);
        }

        public boolean successful() {
            return failure == null;
        }
    }

    private record CandidateReference(ProductCandidate candidate, CatalogProductReference reference) {
    }
}
