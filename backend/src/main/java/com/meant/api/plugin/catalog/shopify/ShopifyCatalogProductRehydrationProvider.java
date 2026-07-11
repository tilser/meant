package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.CommercialFactsFreshness;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.RehydratedCommercialFacts;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationProvider;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogContext;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogLookupRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Rehydrates Shopify references through the existing Global Catalog lookup capability. */
@Component
public class ShopifyCatalogProductRehydrationProvider implements CatalogProductRehydrationProvider {
    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties catalogProperties;
    private final ShopifyCatalogDataUseProperties dataUseProperties;
    private final Clock clock;

    @Autowired
    public ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties
    ) {
        this(provider, catalogProperties, dataUseProperties, Clock.systemUTC());
    }

    ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties,
            Clock clock
    ) {
        this.provider = provider;
        this.catalogProperties = catalogProperties;
        this.dataUseProperties = dataUseProperties;
        this.clock = clock;
    }

    @Override
    public String metricsKey() {
        return "shopify";
    }

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return source != null && source.equals(provider.discoverySourceIdentity());
    }

    @Override
    public List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    ) {
        Map<String, ProductCandidate> candidates = new LinkedHashMap<>();
        CatalogRehydrationFailureKind batchFailure = null;
        for (int start = 0; start < references.size(); start += catalogProperties.maximumLookupIds()) {
            List<CatalogProductReference> batch = references.subList(
                    start,
                    Math.min(start + catalogProperties.maximumLookupIds(), references.size())
            );
            CatalogSourceResult result = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(
                    batch.stream().map(reference -> reference.externalProductReference().value()).distinct().toList(),
                    shopifyContext(context),
                    null
            ));
            if (!result.successful()) {
                batchFailure = CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE;
                continue;
            }
            for (ProductCandidate candidate : result.candidates()) {
                for (ResultProvenance provenance : candidate.provenance()) {
                    candidates.putIfAbsent(candidateKey(
                            provenance.externalProductReference().value(),
                            provenance.externalVariantReference() == null
                                    ? null
                                    : provenance.externalVariantReference().value()
                    ), candidate);
                }
            }
        }
        List<CatalogProductRehydrationResult> results = new ArrayList<>();
        for (CatalogProductReference reference : references) {
            ProductCandidate candidate = candidate(candidates, reference);
            if (candidate == null) {
                results.add(CatalogProductRehydrationResult.failed(
                        reference,
                        batchFailure == null ? CatalogRehydrationStatus.UNAVAILABLE : CatalogRehydrationStatus.DEGRADED,
                        batchFailure == null ? CatalogRehydrationFailureKind.NOT_FOUND : batchFailure
                ));
                continue;
            }
            Instant observedAt = clock.instant();
            ResultFreshness freshness = new ResultFreshness(
                    observedAt,
                    observedAt.plus(dataUseProperties.rehydratedFactsTtl())
            );
            results.add(CatalogProductRehydrationResult.fresh(reference, new RehydratedCommercialFacts(
                    candidate.title(),
                    candidate.offer().price(),
                    candidate.offer().availability(),
                    candidate.offer().identity().externalVariantIdentity(),
                    candidate.offer().selectedOptions(),
                    candidate.offer().delivery(),
                    candidate.media(),
                    freshness,
                    new CommercialFactsFreshness(
                            candidate.offer().price() == null ? null : freshness,
                            freshness,
                            candidate.offer().identity().externalVariantIdentity() == null ? null : freshness,
                            candidate.offer().selectedOptions().isEmpty() ? null : freshness,
                            candidate.offer().delivery().isEmpty() ? null : freshness
                    )
            )));
        }
        return List.copyOf(results);
    }

    private ProductCandidate candidate(
            Map<String, ProductCandidate> candidates,
            CatalogProductReference reference
    ) {
        String productId = reference.externalProductReference().value();
        String variantId = reference.externalVariantReference() == null
                ? null
                : reference.externalVariantReference().value();
        ProductCandidate exact = candidates.get(candidateKey(productId, variantId));
        if (exact != null || variantId != null) {
            return exact;
        }
        return candidates.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(productId + "\u0000"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String candidateKey(String productId, String variantId) {
        return productId + "\u0000" + (variantId == null ? "" : variantId);
    }

    private ShopifyCatalogContext shopifyContext(CatalogRehydrationContext context) {
        return new ShopifyCatalogContext(
                context == null ? null : context.country(),
                null,
                null,
                context == null ? null : context.language(),
                null,
                "Rehydrate selected product"
        );
    }
}
