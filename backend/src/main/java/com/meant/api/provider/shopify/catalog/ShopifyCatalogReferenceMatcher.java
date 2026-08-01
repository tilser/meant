package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Exact seller/product/variant/options matcher that also builds the provider-observed reference. */
@Component
public class ShopifyCatalogReferenceMatcher {
    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    public boolean validRequest(CatalogProductReference reference) {
        return reference.localMerchantId() == null
                && reference.localRouting() == null
                && reference.externalMerchantReference() != null
                && reference.externalVariantReference() != null;
    }

    public Match match(CatalogProductReference requested, List<ProductCandidate> candidates) {
        return analyze(requested, candidates).match();
    }

    MatchAnalysis analyze(CatalogProductReference requested, List<ProductCandidate> candidates) {
        List<Observation> observations = candidates.stream()
                .flatMap(candidate -> candidate.provenance().stream()
                        .map(provenance -> new Observation(candidate, provenance)))
                .toList();
        if (observations.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.NO_CANDIDATES, candidates.size(), 0);
        }
        List<Observation> remaining = observations.stream()
                .filter(observation -> requested.discoverySource().equals(observation.provenance().discoverySource()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.DISCOVERY_SOURCE_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> requested.externalMerchantReference().equals(merchant(observation)))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.MERCHANT_REFERENCE_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> domainMatches(
                        requested.externalMerchantDomain(), observation.provenance().externalMerchantDomain()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.MERCHANT_DOMAIN_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> requested.externalProductReference()
                        .equals(observation.provenance().externalProductReference()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.PRODUCT_REFERENCE_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> requested.externalVariantReference()
                        .equals(observation.candidate().offer().identity().externalVariantIdentity()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.VARIANT_REFERENCE_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> optionsMatch(
                        requested.selectedOptions(), observation.candidate().offer().identity().selectedOptions()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.SELECTED_OPTIONS_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> requested.components()
                        .equals(observation.candidate().offer().identity().components()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.COMPONENTS_MISMATCH,
                    candidates.size(), observations.size());
        }
        remaining = remaining.stream()
                .filter(observation -> Objects.equals(
                        requested.sellingPlanIdentity(),
                        observation.candidate().offer().identity().sellingPlanIdentity()))
                .toList();
        if (remaining.isEmpty()) {
            return MatchAnalysis.failed(MismatchReason.SELLING_PLAN_MISMATCH,
                    candidates.size(), observations.size());
        }
        List<Match> matches = remaining.stream()
                .map(observation -> resolvedMatch(requested, observation))
                .distinct()
                .toList();
        return matches.size() == 1
                ? new MatchAnalysis(matches.getFirst(), MismatchReason.MATCHED,
                        candidates.size(), observations.size(), 1)
                : new MatchAnalysis(null, MismatchReason.AMBIGUOUS_MATCH,
                        candidates.size(), observations.size(), matches.size());
    }

    private ExternalIdentifier merchant(Observation observation) {
        OfferIdentity offer = observation.candidate().offer().identity();
        ExternalIdentifier merchant = offer.merchantScope().externalMerchantIdentity();
        if (merchant == null) {
            merchant = observation.provenance().externalMerchantReference();
        }
        return merchant;
    }

    private Match resolvedMatch(CatalogProductReference requested, Observation observation) {
        ProductCandidate candidate = observation.candidate();
        ResultProvenance provenance = observation.provenance();
        OfferIdentity offer = candidate.offer().identity();
        ExternalIdentifier merchant = merchant(observation);
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                provenance.discoverySource(),
                null,
                null,
                merchant,
                provenance.externalMerchantDomain(),
                provenance.externalProductReference(),
                offer.externalVariantIdentity(),
                offer.selectedOptions(),
                offer.components(),
                offer.sellingPlanIdentity()
        );
        return new Match(resolved, candidate);
    }

    private boolean domainMatches(String requested, String observed) {
        return requested == null || Objects.equals(requested, observed);
    }

    private boolean optionsMatch(List<ProductAttribute> requested, List<ProductAttribute> observed) {
        return requested.isEmpty() || normalized(requested).equals(normalized(observed));
    }

    private List<ProductAttribute> normalized(List<ProductAttribute> options) {
        return options.stream().distinct().sorted(OPTION_ORDER).toList();
    }

    public record Match(CatalogProductReference reference, ProductCandidate candidate) {
    }

    record MatchAnalysis(
            Match match,
            MismatchReason reason,
            int candidateCount,
            int provenanceCount,
            int exactMatchCount
    ) {
        private static MatchAnalysis failed(
                MismatchReason reason,
                int candidateCount,
                int provenanceCount
        ) {
            return new MatchAnalysis(null, reason, candidateCount, provenanceCount, 0);
        }
    }

    enum MismatchReason {
        MATCHED,
        NO_CANDIDATES,
        DISCOVERY_SOURCE_MISMATCH,
        MERCHANT_REFERENCE_MISMATCH,
        MERCHANT_DOMAIN_MISMATCH,
        PRODUCT_REFERENCE_MISMATCH,
        VARIANT_REFERENCE_MISMATCH,
        SELECTED_OPTIONS_MISMATCH,
        COMPONENTS_MISMATCH,
        SELLING_PLAN_MISMATCH,
        AMBIGUOUS_MATCH
    }

    private record Observation(ProductCandidate candidate, ResultProvenance provenance) {
    }
}
