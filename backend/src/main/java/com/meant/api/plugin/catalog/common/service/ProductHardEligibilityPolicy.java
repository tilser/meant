package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Applies explicit hard constraints fail-closed using only comparable typed facts. */
@Component
public class ProductHardEligibilityPolicy {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final List<String> HIERARCHY_SEPARATORS = List.of(" > ", " / ", " :: ");

    List<CanonicalProduct> eligible(List<CanonicalProduct> products, ProductRankingContext context) {
        return products == null ? List.of() : products.stream()
                .filter(Objects::nonNull)
                .filter(product -> offerEligible(product, context))
                .filter(product -> categoryEligible(product, context.hardFilters()))
                .toList();
    }

    private boolean offerEligible(CanonicalProduct product, ProductRankingContext context) {
        CatalogSearchPriceFilter filter = context.hardFilters() == null ? null : context.hardFilters().price();
        boolean priceActive = filter != null && (filter.min() != null || filter.max() != null);
        if (!priceActive) {
            return product.offers().stream().anyMatch(offer -> availabilityEligible(offer.availability().status()));
        }
        String currency = context.searchContext() == null ? null : normalizedCurrency(context.searchContext().currency());
        if (currency == null) {
            return false;
        }
        return product.offers().stream().anyMatch(offer -> availabilityEligible(offer.availability().status())
                && priceEligible(offer.price(), currency, filter));
    }

    private boolean availabilityEligible(OfferAvailabilityStatus status) {
        return switch (status) {
            case IN_STOCK, PREORDER, BACKORDER, UNKNOWN -> true;
            case OUT_OF_STOCK, DISCONTINUED -> false;
        };
    }

    private boolean priceEligible(Money price, String currency, CatalogSearchPriceFilter filter) {
        return price != null
                && currency.equals(price.currency())
                && (filter.min() == null || price.minorUnits() >= filter.min())
                && (filter.max() == null || price.minorUnits() <= filter.max());
    }

    private boolean categoryEligible(CanonicalProduct product, CatalogSearchFilters filters) {
        if (filters == null || filters.categories() == null || filters.categories().isEmpty()) {
            return true;
        }
        Set<String> facts = product.attributes().stream()
                .filter(this::categoryAttribute)
                .map(ProductAttribute::value)
                .map(ProductHardEligibilityPolicy::normalizedCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (facts.isEmpty()) {
            return false;
        }
        return filters.categories().stream()
                .map(ProductHardEligibilityPolicy::normalizedCategory)
                .filter(Objects::nonNull)
                .anyMatch(required -> facts.stream().anyMatch(fact -> hierarchyMatch(fact, required)));
    }

    private boolean categoryAttribute(ProductAttribute attribute) {
        return "category".equals(normalizedCategory(attribute.group()))
                || "category".equals(normalizedCategory(attribute.name()));
    }

    private static boolean hierarchyMatch(String fact, String required) {
        if (fact.equals(required)) {
            return true;
        }
        return HIERARCHY_SEPARATORS.stream().anyMatch(separator -> fact.startsWith(required + separator));
    }

    private static String normalizedCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ");
    }

    private static String normalizedCurrency(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
