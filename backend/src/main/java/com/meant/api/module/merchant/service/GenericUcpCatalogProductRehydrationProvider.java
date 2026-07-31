package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.catalog.service.port.CatalogProductDetailProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reuses lookup/get-product after server-side merchant routing verification. */
@Component
public class GenericUcpCatalogProductRehydrationProvider
        implements CatalogProductRehydrationProvider, CatalogProductDetailProvider {
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
        return referenceVerifier.supportsSource(source);
    }

    @Override
    public boolean supportsDetails(DiscoverySourceIdentity source) {
        return supports(source);
    }

    @Override
    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        return getDetails(reference, null, context);
    }

    @Override
    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        MerchantIntegrationResult integration = referenceVerifier.verify(List.of(reference)).get(reference);
        if (integration == null) {
            return CatalogProductDetailResult.from(unverified(reference), null);
        }
        try {
            ProductDetailsResult details = getProduct(reference, integration, selection, context);
            CatalogProductRehydrationResult rehydrated = observationMapper.map(
                    reference, integration, details, selection);
            if (rehydrated.status() != CatalogRehydrationStatus.FRESH) {
                return CatalogProductDetailResult.from(rehydrated, null);
            }
            var projection = observationMapper.details(
                    details,
                    rehydrated.resolvedReference(),
                    integration.merchantName(),
                    selection
            );
            return projection == null
                    ? CatalogProductDetailResult.failed(
                            reference,
                            CatalogRehydrationStatus.UNAVAILABLE,
                            CatalogRehydrationFailureKind.INVALID_RESPONSE)
                    : CatalogProductDetailResult.from(rehydrated, projection);
        } catch (RuntimeException exception) {
            return CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
        }
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
            ProductDetailsResult details = getProduct(reference, integration, context);
            return observationMapper.map(reference, integration, details);
        } catch (RuntimeException exception) {
            return CatalogProductRehydrationResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
        }
    }

    private ProductDetailsResult getProduct(
            CatalogProductReference reference,
            MerchantIntegrationResult integration,
            CatalogRehydrationContext context
    ) {
        return getProduct(
                reference,
                integration,
                new CatalogProductDetailSelection(reference.selectedOptions(), List.of()),
                context);
    }

    private ProductDetailsResult getProduct(
            CatalogProductReference reference,
            MerchantIntegrationResult integration,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        String country = context == null ? null : context.country();
        String language = context == null ? null : context.language();
        String currency = context == null ? null : context.currency();
        GetMerchantProductDetailsQuery query = selection == null
                ? new GetMerchantProductDetailsQuery(
                        integration.merchantId(),
                        reference.externalProductReference().value(),
                        country,
                        language,
                        currency)
                : new GetMerchantProductDetailsQuery(
                        integration.merchantId(),
                        reference.externalProductReference().value(),
                        country,
                        language,
                        currency,
                        selection.selectedOptions(),
                        selection.preferences());
        return productDetailsService.get(query);
    }

    private CatalogProductRehydrationResult unverified(CatalogProductReference reference) {
        boolean unsupported = reference.localMerchantId() == null && reference.localRouting() == null;
        return CatalogProductRehydrationResult.failed(
                reference,
                unsupported ? CatalogRehydrationStatus.UNSUPPORTED : CatalogRehydrationStatus.UNAVAILABLE,
                unsupported
                        ? CatalogRehydrationFailureKind.CAPABILITY_UNAVAILABLE
                        : CatalogRehydrationFailureKind.INVALID_REFERENCE
        );
    }
}
