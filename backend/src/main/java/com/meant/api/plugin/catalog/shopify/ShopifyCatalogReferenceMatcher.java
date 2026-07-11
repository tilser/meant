package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
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
        List<Match> matches = candidates.stream()
                .flatMap(candidate -> candidate.provenance().stream().map(provenance -> match(
                        requested,
                        candidate,
                        provenance
                )))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private Match match(
            CatalogProductReference requested,
            ProductCandidate candidate,
            ResultProvenance provenance
    ) {
        OfferIdentity offer = candidate.offer().identity();
        ExternalIdentifier merchant = offer.merchantScope().externalMerchantIdentity();
        if (merchant == null) {
            merchant = provenance.externalMerchantReference();
        }
        ExternalIdentifier variant = offer.externalVariantIdentity();
        if (!requested.discoverySource().equals(provenance.discoverySource())
                || !requested.externalMerchantReference().equals(merchant)
                || !requested.externalProductReference().equals(provenance.externalProductReference())
                || !requested.externalVariantReference().equals(variant)
                || !optionsMatch(requested.selectedOptions(), offer.selectedOptions())) {
            return null;
        }
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                provenance.discoverySource(),
                null,
                null,
                merchant,
                provenance.externalProductReference(),
                variant,
                offer.selectedOptions()
        );
        return new Match(resolved, candidate);
    }

    private boolean optionsMatch(List<ProductAttribute> requested, List<ProductAttribute> observed) {
        return requested.isEmpty() || normalized(requested).equals(normalized(observed));
    }

    private List<ProductAttribute> normalized(List<ProductAttribute> options) {
        return options.stream().distinct().sorted(OPTION_ORDER).toList();
    }

    public record Match(CatalogProductReference reference, ProductCandidate candidate) {
    }
}
