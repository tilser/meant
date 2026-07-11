package com.meant.api.module.merchant.service;

import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves one exact, unambiguous variant observation from a generic UCP product response. */
@Component
public class GenericUcpVariantObservationResolver {
    public Resolution resolve(CatalogProductReference reference, ProductDetailsResponse.Product product) {
        List<ProductAttribute> requestedOptions = VariantObservation.options(reference.selectedOptions());
        List<VariantObservation> observations = new ArrayList<>();
        if (product.selectedOrFirstAvailableVariant() != null) {
            observations.add(VariantObservation.from(product.selectedOrFirstAvailableVariant()));
        }
        if (product.variants() != null) {
            product.variants().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(VariantObservation::from)
                    .forEach(observations::add);
        }
        List<VariantObservation> matches = observations.stream()
                .filter(observation -> reference.externalVariantReference().value().equals(observation.id()))
                .filter(observation -> requestedOptions.isEmpty()
                        || requestedOptions.equals(observation.options()))
                .distinct()
                .toList();
        if (matches.isEmpty()) {
            return Resolution.failed(CatalogRehydrationFailureKind.NOT_FOUND);
        }
        if (matches.size() != 1) {
            return Resolution.failed(CatalogRehydrationFailureKind.INVALID_RESPONSE);
        }
        return new Resolution(matches.getFirst(), null);
    }

    public record Resolution(VariantObservation observation, CatalogRehydrationFailureKind failure) {
        private static Resolution failed(CatalogRehydrationFailureKind failure) {
            return new Resolution(null, failure);
        }
    }

    public record VariantObservation(
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

        private static VariantObservation from(ProductDetailsResponse.SelectedVariant variant) {
            return new VariantObservation(variant.variantId(), variant.price(), variant.currency(), variant.imageUrl(),
                    variant.available(), options(variant.selectedOptions()));
        }

        private static VariantObservation from(ProductDetailsResponse.Variant variant) {
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

        private static List<ProductAttribute> options(java.util.Collection<ProductAttribute> options) {
            if (options == null) {
                return List.of();
            }
            return options.stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted(OPTION_ORDER)
                    .toList();
        }
    }
}
