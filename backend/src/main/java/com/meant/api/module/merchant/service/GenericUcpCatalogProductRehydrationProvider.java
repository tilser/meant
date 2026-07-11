package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reuses lookup/get-product after server-side merchant routing verification. */
@Component
public class GenericUcpCatalogProductRehydrationProvider implements CatalogProductRehydrationProvider {
    private final MerchantProductDetailsService productDetailsService;
    private final GenericUcpCatalogReferenceVerifier referenceVerifier;
    private final GenericUcpProductObservationMapper observationMapper;

    public GenericUcpCatalogProductRehydrationProvider(
            MerchantProductDetailsService productDetailsService,
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpProductObservationMapper observationMapper
    ) {
        this.productDetailsService = productDetailsService;
        this.referenceVerifier = referenceVerifier;
        this.observationMapper = observationMapper;
    }

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return MerchantCatalogSourceIdentity.DISCOVERY_SOURCE.equals(source);
    }

    @Override
    public List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    ) {
        Map<CatalogProductReference, MerchantIntegrationResult> verified = referenceVerifier.verify(references);
        List<CatalogProductRehydrationResult> results = new ArrayList<>();
        for (CatalogProductReference reference : references) {
            MerchantIntegrationResult integration = verified.get(reference);
            if (integration == null) {
                results.add(unverified(reference));
                continue;
            }
            results.add(remote(reference, integration, context));
        }
        return List.copyOf(results);
    }

    private CatalogProductRehydrationResult remote(
            CatalogProductReference reference,
            MerchantIntegrationResult integration,
            CatalogRehydrationContext context
    ) {
        try {
            ProductDetailsResult details = productDetailsService.get(new GetMerchantProductDetailsQuery(
                    integration.merchantId(),
                    reference.externalProductReference().value(),
                    context == null ? null : context.country(),
                    context == null ? null : context.language()
            ));
            return observationMapper.map(reference, integration, details);
        } catch (RuntimeException exception) {
            return CatalogProductRehydrationResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
        }
    }

    private CatalogProductRehydrationResult unverified(CatalogProductReference reference) {
        boolean unsupported = reference.localMerchantId() == null;
        return CatalogProductRehydrationResult.failed(
                reference,
                unsupported ? CatalogRehydrationStatus.UNSUPPORTED : CatalogRehydrationStatus.UNAVAILABLE,
                unsupported
                        ? CatalogRehydrationFailureKind.CAPABILITY_UNAVAILABLE
                        : CatalogRehydrationFailureKind.INVALID_REFERENCE
        );
    }
}
