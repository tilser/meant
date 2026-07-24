package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.merchant.service.MerchantPresentationOriginService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adds verified buyer-facing merchant origins without changing provider routing coordinates. */
@Service
@RequiredArgsConstructor
public class UserCatalogMerchantOriginEnrichmentService {

    private final MerchantPresentationOriginService merchantPresentationOriginService;

    public List<ProductCandidate> enrichCandidates(List<ProductCandidate> candidates) {
        List<ProductCandidate> values = candidates == null
                ? List.of()
                : candidates.stream().filter(Objects::nonNull).toList();
        if (merchantPresentationOriginService == null) {
            return values;
        }
        Map<ResultProvenance, String> origins = merchantPresentationOriginService.resolveAll(
                values.stream()
                        .flatMap(candidate -> java.util.stream.Stream.concat(
                                candidate.provenance().stream(),
                                candidate.offer().provenance().stream()
                        ))
                        .distinct()
                        .toList()
        );
        return values.stream().map(candidate -> enrich(candidate, origins)).toList();
    }

    public List<CanonicalProduct> enrichProducts(List<CanonicalProduct> products) {
        List<CanonicalProduct> values = products == null
                ? List.of()
                : products.stream().filter(Objects::nonNull).toList();
        if (merchantPresentationOriginService == null) {
            return values;
        }
        Map<ResultProvenance, String> origins = merchantPresentationOriginService.resolveAll(
                values.stream()
                        .flatMap(product -> java.util.stream.Stream.concat(
                                product.provenance().stream(),
                                product.offers().stream().flatMap(offer -> offer.provenance().stream())
                        ))
                        .distinct()
                        .toList()
        );
        return values.stream().map(product -> enrich(product, origins)).toList();
    }

    public ProductCandidate enrichCandidate(ProductCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        if (merchantPresentationOriginService == null) {
            return candidate;
        }
        Map<ResultProvenance, String> origins = merchantPresentationOriginService.resolveAll(
                java.util.stream.Stream.concat(
                                candidate.provenance().stream(),
                                candidate.offer().provenance().stream()
                        )
                        .distinct()
                        .toList()
        );
        return enrich(candidate, origins);
    }

    public CanonicalProduct enrichProduct(CanonicalProduct product) {
        return enrichProducts(product == null ? List.of() : List.of(product)).stream()
                .findFirst()
                .orElse(null);
    }

    private ProductCandidate enrich(
            ProductCandidate candidate,
            Map<ResultProvenance, String> origins
    ) {
        return new ProductCandidate(
                candidate.title(),
                candidate.description(),
                candidate.media(),
                candidate.attributes(),
                candidate.materials(),
                candidate.certifications(),
                candidate.attribution(),
                candidate.identityEvidence(),
                enrich(candidate.provenance(), origins),
                candidate.retrievalSignals(),
                enrich(candidate.offer(), origins)
        );
    }

    private CanonicalProduct enrich(
            CanonicalProduct product,
            Map<ResultProvenance, String> origins
    ) {
        return new CanonicalProduct(
                product.key(),
                product.title(),
                product.description(),
                product.media(),
                product.attributes(),
                product.materials(),
                product.certifications(),
                product.attribution(),
                product.identityEvidence(),
                enrich(product.provenance(), origins),
                product.retrievalSignals(),
                product.offers().stream().map(offer -> enrich(offer, origins)).toList()
        );
    }

    private Offer enrich(Offer offer, Map<ResultProvenance, String> origins) {
        return new Offer(
                offer.identity(),
                offer.merchantName(),
                offer.variantTitle(),
                offer.price(),
                offer.listPrice(),
                offer.availability(),
                offer.delivery(),
                offer.checkoutUrl(),
                offer.rankingEvidence(),
                enrich(offer.provenance(), origins)
        );
    }

    private List<ResultProvenance> enrich(
            Collection<ResultProvenance> provenance,
            Map<ResultProvenance, String> origins
    ) {
        return provenance.stream()
                .map(source -> {
                    String origin = origins.get(source);
                    return origin == null ? source.withMerchantOrigin(null) : source.withMerchantOrigin(origin);
                })
                .toList();
    }
}
