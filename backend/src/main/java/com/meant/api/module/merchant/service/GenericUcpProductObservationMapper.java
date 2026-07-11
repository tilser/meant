package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CommercialFactsFreshness;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.RehydratedCommercialFacts;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Selects the exact requested UCP variant/options and maps its current typed facts. */
@Component
public class GenericUcpProductObservationMapper {
    private final GenericUcpCatalogReferenceVerifier referenceVerifier;
    private final GenericUcpCatalogDataUseProperties properties;
    private final Clock clock;

    @Autowired
    public GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpCatalogDataUseProperties properties
    ) {
        this(referenceVerifier, properties, Clock.systemUTC());
    }

    GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpCatalogDataUseProperties properties,
            Clock clock
    ) {
        this.referenceVerifier = referenceVerifier;
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
        VariantObservation variant = exactVariant(reference, product);
        if (variant == null) {
            return failed(reference, CatalogRehydrationFailureKind.NOT_FOUND);
        }
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

    private VariantObservation exactVariant(CatalogProductReference reference, ProductDetailsResponse.Product product) {
        List<VariantObservation> variants = new ArrayList<>();
        if (product.selectedOrFirstAvailableVariant() != null) {
            variants.add(VariantObservation.from(product.selectedOrFirstAvailableVariant()));
        }
        if (product.variants() != null) {
            product.variants().stream().map(VariantObservation::from).forEach(variants::add);
        }
        return variants.stream()
                .filter(variant -> reference.externalVariantReference().value().equals(variant.id()))
                .filter(variant -> reference.selectedOptions().isEmpty()
                        || reference.selectedOptions().equals(variant.options()))
                .distinct()
                .findFirst()
                .orElse(null);
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

    private record VariantObservation(
            String id,
            String price,
            String currency,
            String imageUrl,
            Boolean available,
            List<ProductAttribute> options
    ) {
        private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
                .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
                .thenComparing(ProductAttribute::name)
                .thenComparing(ProductAttribute::value);

        static VariantObservation from(ProductDetailsResponse.SelectedVariant variant) {
            return new VariantObservation(variant.variantId(), variant.price(), variant.currency(), variant.imageUrl(),
                    variant.available(), options(variant.selectedOptions()));
        }

        static VariantObservation from(ProductDetailsResponse.Variant variant) {
            return new VariantObservation(variant.variantId(), variant.price(), variant.currency(), variant.imageUrl(),
                    variant.available(), options(variant.selectedOptions()));
        }

        private static List<ProductAttribute> options(List<ProductDetailsResponse.SelectedOption> options) {
            if (options == null) {
                return List.of();
            }
            return options.stream()
                    .filter(option -> option != null && option.name() != null && option.value() != null)
                    .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                    .distinct()
                    .sorted(OPTION_ORDER)
                    .toList();
        }
    }
}
