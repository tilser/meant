package com.meant.api.module.catalog.service.dto;

import com.meant.api.module.catalog.service.support.CanonicalCommerceKey;
import java.util.Comparator;
import java.util.List;

/** Stable commercial identity for a seller/product/variant/configuration offer. */
public record OfferIdentity(
        ProviderIdentity provider,
        OfferMerchantScope merchantScope,
        ExternalIdentifier externalProductIdentity,
        ExternalIdentifier externalVariantIdentity,
        List<ProductAttribute> selectedOptions,
        List<OfferComponentIdentity> components,
        SellingPlanIdentity sellingPlanIdentity
) {

    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute value) -> value.group() == null ? "" : value.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    public OfferIdentity {
        if (provider == null
                || merchantScope == null
                || externalProductIdentity == null) {
            throw new IllegalArgumentException("Required offer identity fields must not be null");
        }
        if (merchantScope.externalMerchantIdentity() != null
                && !provider.value().equals(merchantScope.externalMerchantIdentity().namespace())) {
            throw new IllegalArgumentException("External merchant identity must use the provider namespace");
        }
        requireProviderIdentifier(externalProductIdentity, ExternalIdentifierType.PRODUCT, provider);
        if (externalVariantIdentity != null) {
            requireProviderIdentifier(externalVariantIdentity, ExternalIdentifierType.VARIANT, provider);
        }
        selectedOptions = selectedOptions == null
                ? List.of()
                : selectedOptions.stream().distinct().sorted(OPTION_ORDER).toList();
        components = components == null
                ? List.of()
                : components.stream().sorted(OfferComponentIdentity::compareCanonical).toList();
        for (OfferComponentIdentity component : components) {
            requireProviderIdentifier(component.externalProductIdentity(), ExternalIdentifierType.PRODUCT, provider);
            if (component.externalVariantIdentity() != null) {
                requireProviderIdentifier(component.externalVariantIdentity(), ExternalIdentifierType.VARIANT, provider);
            }
        }
        if (sellingPlanIdentity != null) {
            if (sellingPlanIdentity.groupReference() != null) {
                requireProviderIdentifier(
                        sellingPlanIdentity.groupReference(), ExternalIdentifierType.SELLING_PLAN_GROUP, provider);
            }
            if (sellingPlanIdentity.planReference() != null) {
                requireProviderIdentifier(
                        sellingPlanIdentity.planReference(), ExternalIdentifierType.SELLING_PLAN, provider);
            }
        }
    }

    public String key() {
        return CanonicalCommerceKey.offerKey(this);
    }

    private static void requireProviderIdentifier(
            ExternalIdentifier identifier,
            ExternalIdentifierType expectedType,
            ProviderIdentity provider
    ) {
        if (identifier.type() != expectedType || !provider.value().equals(identifier.namespace())) {
            throw new IllegalArgumentException(
                    "%s identity must use its expected type and provider namespace".formatted(expectedType));
        }
    }
}
