package com.meant.api.module.merchant.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MerchantCatalogProductCandidate(
        MerchantSemanticSearchResult merchant,
        String endpoint,
        CatalogSearchResponse.Product product,
        int catalogRank
) {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    public String productKey() {
        return merchant.domain() + ":" + product.id();
    }

    public String rerankDocument() {
        return Stream.of(
                        labeled("Product", product.title()),
                        labeled("Description", plainDescription()),
                        labeled("Categories", categories()),
                        labeled("Tags", tags()),
                        labeled("Merchant", merchant.name() + " (" + merchant.domain() + ")")
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n"));
    }

    public String descriptionHtml() {
        return product.description() == null ? null : product.description().html();
    }

    public String imageUrl() {
        return Stream.concat(
                        safeNonNullList(product.media()).stream(),
                        safeNonNullList(product.variants()).stream()
                                .flatMap(variant -> safeNonNullList(variant.media()).stream())
                )
                .map(CatalogSearchResponse.Media::url)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public Long priceMinAmount() {
        return Optional.ofNullable(product.priceRange())
                .map(CatalogSearchResponse.PriceRange::min)
                .map(CatalogSearchResponse.Money::amount)
                .orElse(null);
    }

    public Long priceMaxAmount() {
        return Optional.ofNullable(product.priceRange())
                .map(CatalogSearchResponse.PriceRange::max)
                .map(CatalogSearchResponse.Money::amount)
                .orElse(null);
    }

    public String priceCurrency() {
        return Stream.of(
                        Optional.ofNullable(product.priceRange())
                                .map(CatalogSearchResponse.PriceRange::min)
                                .map(CatalogSearchResponse.Money::currency)
                                .orElse(null),
                        Optional.ofNullable(product.priceRange())
                                .map(CatalogSearchResponse.PriceRange::max)
                                .map(CatalogSearchResponse.Money::currency)
                                .orElse(null)
                )
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public Boolean available() {
        List<Boolean> availability = safeNonNullList(product.variants()).stream()
                .map(CatalogSearchResponse.Variant::availability)
                .filter(Objects::nonNull)
                .map(CatalogSearchResponse.Availability::available)
                .filter(Objects::nonNull)
                .toList();
        if (availability.isEmpty()) {
            return null;
        }
        return availability.stream().anyMatch(Boolean.TRUE::equals);
    }

    public List<String> categoryValues() {
        return safeNonNullList(product.categories()).stream()
                .map(CatalogSearchResponse.Category::value)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    public List<String> tagValues() {
        return safeList(product.tags()).stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private String plainDescription() {
        String description = descriptionHtml();
        if (description == null || description.isBlank()) {
            return null;
        }
        return SPACE_PATTERN.matcher(HTML_TAG_PATTERN.matcher(description).replaceAll(" "))
                .replaceAll(" ")
                .trim();
    }

    private String categories() {
        return safeNonNullList(product.categories()).stream()
                .map(CatalogSearchResponse.Category::value)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String tags() {
        return safeList(product.tags()).stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String labeled(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value.trim();
    }

}
