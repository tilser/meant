package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpVariantObservationResolver.VariantObservation;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Selects the exact requested UCP variant/options and maps its current typed facts. */
@Component
public class GenericUcpProductObservationMapper {
    private final GenericUcpCatalogReferenceVerifier referenceVerifier;
    private final GenericUcpVariantObservationResolver variantResolver;
    private final GenericUcpCatalogDataUseProperties properties;
    private final Clock clock;

    @Autowired
    public GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpVariantObservationResolver variantResolver,
            GenericUcpCatalogDataUseProperties properties
    ) {
        this(referenceVerifier, variantResolver, properties, Clock.systemUTC());
    }

    GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpVariantObservationResolver variantResolver,
            GenericUcpCatalogDataUseProperties properties,
            Clock clock
    ) {
        this.referenceVerifier = referenceVerifier;
        this.variantResolver = variantResolver;
        this.properties = properties;
        this.clock = clock;
    }

    public CatalogProductRehydrationResult map(
            CatalogProductReference reference,
            MerchantIntegrationResult integration,
            ProductDetailsResult details
    ) {
        ProductDetailsResponse.Product product = details == null ? null : details.product();
        if (product == null || !reference.externalProductReference().value().equals(product.productId())) {
            return failed(reference, CatalogRehydrationFailureKind.NOT_FOUND);
        }
        if (reference.externalVariantReference() == null) {
            return failed(reference, CatalogRehydrationFailureKind.INVALID_REFERENCE);
        }
        GenericUcpVariantObservationResolver.Resolution resolution = variantResolver.resolve(reference, product);
        if (resolution.failure() != null) {
            return failed(reference, resolution.failure());
        }
        VariantObservation variant = resolution.observation();
        Instant observedAt = clock.instant();
        ResultFreshness freshness = new ResultFreshness(observedAt, observedAt.plus(properties.rehydratedFactsTtl()));
        ExternalIdentifier productId = identifier(ExternalIdentifierType.PRODUCT, product.productId());
        ExternalIdentifier variantId = identifier(ExternalIdentifierType.VARIANT, variant.id());
        CatalogProductReference resolved = referenceVerifier.canonical(
                reference, integration, productId, variantId, variant.options());
        Money price = money(variant.price(), variant.currency());
        return CatalogProductRehydrationResult.fresh(reference, resolved, new RehydratedCommercialFacts(
                product.title(),
                price,
                availability(variant.available()),
                variantId,
                variant.options(),
                List.of(),
                media(product, variant),
                freshness,
                new CommercialFactsFreshness(
                        price == null ? null : freshness,
                        variant.available() == null ? null : freshness,
                        freshness,
                        variant.options().isEmpty() ? null : freshness,
                        null
                )
        ));
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, MerchantCatalogSourceIdentity.PROVIDER.value(), value);
    }

    private Money money(String price, String currency) {
        Long amount = UcpMoney.minorAmount(price, currency);
        return amount == null || currency == null ? null : new Money(amount, currency);
    }

    private OfferAvailability availability(Boolean available) {
        return new OfferAvailability(available == null ? OfferAvailabilityStatus.UNKNOWN
                : available ? OfferAvailabilityStatus.IN_STOCK : OfferAvailabilityStatus.OUT_OF_STOCK, null, null);
    }

    private List<ProductMedia> media(ProductDetailsResponse.Product product, VariantObservation variant) {
        return java.util.stream.Stream.concat(
                        product.images() == null ? java.util.stream.Stream.empty()
                                : product.images().stream().map(ProductDetailsResponse.Image::url),
                        java.util.stream.Stream.of(variant.imageUrl(), product.imageUrl()))
                .map(this::httpsUri)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(uri -> new ProductMedia(ProductMediaType.IMAGE, uri, null, null, null))
                .toList();
    }

    private URI httpsUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private CatalogProductRehydrationResult failed(
            CatalogProductReference reference,
            CatalogRehydrationFailureKind failure
    ) {
        return CatalogProductRehydrationResult.failed(reference, CatalogRehydrationStatus.UNAVAILABLE, failure);
    }

}
