package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelectionResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.port.CatalogProductDetailProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Dispatches a single current get-product call without widening batch/cart rehydration. */
@Service
@RequiredArgsConstructor
public class CatalogProductDetailService {
    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    private final List<CatalogProductDetailProvider> providers;
    private final CatalogProductRehydrationMetrics metrics;

    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        return getDetails(reference, null, context);
    }

    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        List<CatalogProductDetailProvider> matching = providers.stream()
                .filter(provider -> provider.supportsDetails(reference.discoverySource()))
                .toList();
        if (matching.size() != 1) {
            CatalogProductDetailResult failed = CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.UNSUPPORTED,
                    matching.isEmpty()
                            ? CatalogRehydrationFailureKind.NO_PROVIDER
                            : CatalogRehydrationFailureKind.AMBIGUOUS_PROVIDER
            );
            metrics.record(failed.rehydration());
            return failed;
        }
        try {
            CatalogProductDetailResult result = selection == null
                    ? matching.getFirst().getDetails(reference, context)
                    : matching.getFirst().getDetails(reference, selection, context);
            if (result == null || !reference.equals(result.rehydration().reference())) {
                result = CatalogProductDetailResult.failed(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.INVALID_RESPONSE
                );
            }
            if (selection != null && result.details() != null) {
                result = result.withSelection(selectionResult(selection, result.details()));
            }
            metrics.record(result.rehydration());
            return result;
        } catch (RuntimeException exception) {
            CatalogProductDetailResult failed = CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
            metrics.record(failed.rehydration());
            return failed;
        }
    }

    private CatalogProductDetailSelectionResult selectionResult(
            CatalogProductDetailSelection requested,
            RehydratedProductDetails details
    ) {
        List<ProductAttribute> requestedOptions = normalized(requested.selectedOptions());
        List<ProductAttribute> effectiveOptions = normalized(details.selected().stream()
                .filter(Objects::nonNull)
                .filter(option -> hasText(option.name()) && hasText(option.value()))
                .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                .toList());
        List<String> optionNames = details.options().stream()
                .filter(Objects::nonNull)
                .map(RehydratedProductDetails.Option::name)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        boolean complete = optionNames.stream().allMatch(name -> effectiveOptions.stream()
                .anyMatch(option -> option.name().equals(name)));
        int matchingVariantCount = matchingVariantCount(details, effectiveOptions);
        return new CatalogProductDetailSelectionResult(
                requestedOptions,
                effectiveOptions,
                complete,
                !requestedOptions.equals(effectiveOptions),
                matchingVariantCount
        );
    }

    private int matchingVariantCount(
            RehydratedProductDetails details,
            List<ProductAttribute> effectiveOptions
    ) {
        List<RehydratedProductDetails.Variant> candidates = new ArrayList<>();
        if (details.selectedVariant() != null) {
            candidates.add(details.selectedVariant());
        }
        candidates.addAll(details.variants());
        Set<String> matchedVariantIds = new HashSet<>();
        int matches = 0;
        for (RehydratedProductDetails.Variant variant : candidates) {
            if (variant == null || !variantOptions(variant).containsAll(effectiveOptions)) {
                continue;
            }
            String variantId = hasText(variant.variantId()) ? variant.variantId().trim() : null;
            if (variantId == null || matchedVariantIds.add(variantId)) {
                matches++;
            }
        }
        return matches;
    }

    private List<ProductAttribute> variantOptions(RehydratedProductDetails.Variant variant) {
        return normalized(variant.selectedOptions().stream()
                .filter(Objects::nonNull)
                .filter(option -> hasText(option.name()) && hasText(option.value()))
                .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                .toList());
    }

    private List<ProductAttribute> normalized(List<ProductAttribute> options) {
        return options == null
                ? List.of()
                : options.stream().filter(Objects::nonNull).distinct().sorted(OPTION_ORDER).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
