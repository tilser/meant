package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.CommercialFactsFreshness;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.RehydratedCommercialFacts;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
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

/** Rehydrates exact Shopify seller offers through the existing Global Catalog lookup capability. */
@Component
public class ShopifyCatalogProductRehydrationProvider implements CatalogProductRehydrationProvider {
    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties catalogProperties;
    private final ShopifyCatalogDataUseProperties dataUseProperties;
    private final ShopifyCatalogReferenceMatcher matcher;
    private final Clock clock;

    @Autowired
    public ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties,
            ShopifyCatalogReferenceMatcher matcher
    ) {
        this(provider, catalogProperties, dataUseProperties, matcher, Clock.systemUTC());
    }

    ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties,
            ShopifyCatalogReferenceMatcher matcher,
            Clock clock
    ) {
        this.provider = provider;
        this.catalogProperties = catalogProperties;
        this.dataUseProperties = dataUseProperties;
        this.matcher = matcher;
        this.clock = clock;
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
        Map<CatalogProductReference, CatalogProductRehydrationResult> results = new LinkedHashMap<>();
        List<CatalogProductReference> valid = references.stream()
                .filter(reference -> {
                    if (matcher.validRequest(reference)) {
                        return true;
                    }
                    results.put(reference, failure(reference, CatalogRehydrationStatus.UNAVAILABLE,
                            CatalogRehydrationFailureKind.INVALID_REFERENCE));
                    return false;
                })
                .toList();
        for (int start = 0; start < valid.size(); start += catalogProperties.maximumLookupIds()) {
            List<CatalogProductReference> batch = valid.subList(
                    start,
                    Math.min(start + catalogProperties.maximumLookupIds(), valid.size())
            );
            try {
                hydrateBatch(batch, context, results);
            } catch (RuntimeException exception) {
                batch.forEach(reference -> results.put(reference, failure(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                )));
            }
        }
        return references.stream().map(results::get).toList();
    }

    private void hydrateBatch(
            List<CatalogProductReference> batch,
            CatalogRehydrationContext context,
            Map<CatalogProductReference, CatalogProductRehydrationResult> results
    ) {
        CatalogSourceResult sourceResult = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(
                batch.stream().map(reference -> reference.externalProductReference().value()).distinct().toList(),
                shopifyContext(context),
                null
        ));
        if (!sourceResult.successful()) {
            batch.forEach(reference -> results.put(reference, failure(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            )));
            return;
        }
        List<ProductCandidate> candidates = sourceResult.candidates() == null ? List.of() : sourceResult.candidates();
        for (CatalogProductReference reference : batch) {
            ShopifyCatalogReferenceMatcher.Match match = matcher.match(reference, candidates);
            results.put(reference, match == null
                    ? failure(reference, CatalogRehydrationStatus.UNAVAILABLE, CatalogRehydrationFailureKind.NOT_FOUND)
                    : fresh(reference, match));
        }
    }

    private CatalogProductRehydrationResult fresh(
            CatalogProductReference requested,
            ShopifyCatalogReferenceMatcher.Match match
    ) {
        Instant observedAt = clock.instant();
        ResultFreshness freshness = new ResultFreshness(
                observedAt,
                observedAt.plus(dataUseProperties.rehydratedFactsTtl())
        );
        ProductCandidate candidate = match.candidate();
        boolean knownAvailability = candidate.offer().availability().status() != OfferAvailabilityStatus.UNKNOWN;
        return CatalogProductRehydrationResult.fresh(requested, match.reference(), new RehydratedCommercialFacts(
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
                        knownAvailability ? freshness : null,
                        candidate.offer().identity().externalVariantIdentity() == null ? null : freshness,
                        candidate.offer().selectedOptions().isEmpty() ? null : freshness,
                        candidate.offer().delivery().isEmpty() ? null : freshness
                )
        ));
    }

    private CatalogProductRehydrationResult failure(
            CatalogProductReference reference,
            CatalogRehydrationStatus status,
            CatalogRehydrationFailureKind failure
    ) {
        return CatalogProductRehydrationResult.failed(reference, status, failure);
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
