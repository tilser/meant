package com.meant.api.module.catalog.service.dto;

import java.util.Comparator;
import java.util.List;

/** Identity-bearing component of a bundle or configured composite offer. */
public record OfferComponentIdentity(
        ExternalIdentifier externalProductIdentity,
        ExternalIdentifier externalVariantIdentity,
        int quantity,
        List<ProductAttribute> selectedOptions
) {

    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute value) -> value.group() == null ? "" : value.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    public OfferComponentIdentity {
        if (externalProductIdentity == null || quantity < 1) {
            throw new IllegalArgumentException("Offer component product identity and positive quantity are required");
        }
        if (externalProductIdentity.type() != ExternalIdentifierType.PRODUCT
                || (externalVariantIdentity != null
                && externalVariantIdentity.type() != ExternalIdentifierType.VARIANT)) {
            throw new IllegalArgumentException("Offer component references must use PRODUCT and VARIANT types");
        }
        selectedOptions = selectedOptions == null
                ? List.of()
                : selectedOptions.stream().sorted(OPTION_ORDER).toList();
    }

    static int compareCanonical(OfferComponentIdentity first, OfferComponentIdentity second) {
        int comparison = compareIdentifier(first.externalProductIdentity, second.externalProductIdentity);
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareIdentifier(first.externalVariantIdentity, second.externalVariantIdentity);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(first.quantity, second.quantity);
        if (comparison != 0) {
            return comparison;
        }
        return compareOptions(first.selectedOptions, second.selectedOptions);
    }

    private static int compareIdentifier(ExternalIdentifier first, ExternalIdentifier second) {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        int comparison = first.type().compareTo(second.type());
        if (comparison != 0) {
            return comparison;
        }
        comparison = nullToEmpty(first.namespace()).compareTo(nullToEmpty(second.namespace()));
        return comparison != 0 ? comparison : first.value().compareTo(second.value());
    }

    private static int compareOptions(List<ProductAttribute> first, List<ProductAttribute> second) {
        int sharedSize = Math.min(first.size(), second.size());
        for (int index = 0; index < sharedSize; index++) {
            int comparison = OPTION_ORDER.compare(first.get(index), second.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
