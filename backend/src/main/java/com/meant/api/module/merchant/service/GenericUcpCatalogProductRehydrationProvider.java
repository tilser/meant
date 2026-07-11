package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.plugin.catalog.common.dto.*;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationProvider;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUseProperties;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reuses the existing merchant lookup then catalog lookup/get-product dispatch sequence. */
@Component
public class GenericUcpCatalogProductRehydrationProvider implements CatalogProductRehydrationProvider {
    private final MerchantProductDetailsService productDetailsService;
    private final GenericUcpCatalogDataUseProperties properties;
    private final Clock clock;

    @Autowired
    public GenericUcpCatalogProductRehydrationProvider(
            MerchantProductDetailsService productDetailsService,
            GenericUcpCatalogDataUseProperties properties
    ) {
        this(productDetailsService, properties, Clock.systemUTC());
    }

    GenericUcpCatalogProductRehydrationProvider(
            MerchantProductDetailsService productDetailsService,
            GenericUcpCatalogDataUseProperties properties,
            Clock clock
    ) {
        this.productDetailsService = productDetailsService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String metricsKey() {
        return "generic_ucp";
    }

    @Override
    public boolean supports(com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity source) {
        return GenericUcpCatalogDataUsePolicy.SOURCE.equals(source);
    }

    @Override
    public List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    ) {
        List<CatalogProductRehydrationResult> results = new ArrayList<>();
        for (CatalogProductReference reference : references) {
            if (reference.localMerchantId() == null) {
                results.add(CatalogProductRehydrationResult.failed(
                        reference,
                        CatalogRehydrationStatus.UNSUPPORTED,
                        CatalogRehydrationFailureKind.CAPABILITY_UNAVAILABLE
                ));
                continue;
            }
            try {
                ProductDetailsResult details = productDetailsService.get(new GetMerchantProductDetailsQuery(
                        reference.localMerchantId(),
                        reference.externalProductReference().value(),
                        context == null ? null : context.country(),
                        context == null ? null : context.language()
                ));
                results.add(result(reference, details));
            } catch (RuntimeException exception) {
                results.add(CatalogProductRehydrationResult.failed(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                ));
            }
        }
        return List.copyOf(results);
    }

    private CatalogProductRehydrationResult result(
            CatalogProductReference reference,
            ProductDetailsResult details
    ) {
        ProductDetailsResponse.Product product = details == null ? null : details.product();
        if (product == null) {
            return CatalogProductRehydrationResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.INVALID_RESPONSE
            );
        }
        ProductDetailsResponse.SelectedVariant variant = product.selectedOrFirstAvailableVariant();
        String currency = variant == null || variant.currency() == null
                ? product.priceRange() == null ? null : product.priceRange().currency()
                : variant.currency();
        String price = variant == null || variant.price() == null
                ? product.priceRange() == null ? null : product.priceRange().min()
                : variant.price();
        Long minorAmount = UcpMoney.minorAmount(price, currency);
        Money money = minorAmount == null || currency == null ? null : new Money(minorAmount, currency);
        ExternalIdentifier selectedVariant = variant == null
                ? reference.externalVariantReference()
                : ExternalIdentifier.optional(
                        ExternalIdentifierType.VARIANT,
                        reference.discoverySource().provider().value(),
                        variant.variantId()
                );
        List<ProductAttribute> selectedOptions = selectedOptions(variant);
        OfferAvailability availability = new OfferAvailability(
                variant == null || variant.available() == null
                        ? OfferAvailabilityStatus.UNKNOWN
                        : Boolean.TRUE.equals(variant.available())
                                ? OfferAvailabilityStatus.IN_STOCK
                                : OfferAvailabilityStatus.OUT_OF_STOCK,
                null,
                null
        );
        Instant observedAt = clock.instant();
        ResultFreshness freshness = new ResultFreshness(
                observedAt,
                observedAt.plus(properties.rehydratedFactsTtl())
        );
        return CatalogProductRehydrationResult.fresh(reference, new RehydratedCommercialFacts(
                product.title(),
                money,
                availability,
                selectedVariant,
                selectedOptions,
                List.of(),
                media(product, variant),
                freshness,
                new CommercialFactsFreshness(
                        money == null ? null : freshness,
                        variant == null || variant.available() == null ? null : freshness,
                        selectedVariant == null ? null : freshness,
                        selectedOptions.isEmpty() ? null : freshness,
                        null
                )
        ));
    }

    private List<ProductAttribute> selectedOptions(ProductDetailsResponse.SelectedVariant variant) {
        if (variant == null || variant.selectedOptions() == null) {
            return List.of();
        }
        return variant.selectedOptions().stream()
                .filter(option -> option != null && hasText(option.name()) && hasText(option.value()))
                .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                .toList();
    }

    private List<ProductMedia> media(
            ProductDetailsResponse.Product product,
            ProductDetailsResponse.SelectedVariant variant
    ) {
        return java.util.stream.Stream.concat(
                        safe(product.images()).stream().map(ProductDetailsResponse.Image::url),
                        java.util.stream.Stream.of(variant == null ? null : variant.imageUrl(), product.imageUrl())
                )
                .map(this::httpsUri)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(uri -> new ProductMedia(ProductMediaType.IMAGE, uri, null, null, null))
                .toList();
    }

    private URI httpsUri(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
