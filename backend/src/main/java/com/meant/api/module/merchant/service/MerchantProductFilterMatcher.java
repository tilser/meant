package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.support.UcpDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantProductFilterMatcher {

    private static final java.util.regex.Pattern AUDIENCE_WORD_SEPARATOR_PATTERN =
            java.util.regex.Pattern.compile("[^a-z0-9]+");
    private static final java.util.regex.Pattern AUDIENCE_SPACE_PATTERN =
            java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern MEN_AUDIENCE_PATTERN =
            java.util.regex.Pattern.compile("\\b(?:men|men s|mens|man|man s|male|males)\\b");
    private static final java.util.regex.Pattern WOMEN_AUDIENCE_PATTERN =
            java.util.regex.Pattern.compile("\\b(?:women|women s|womens|woman|woman s|ladies|lady|female|females)\\b");
    private static final java.util.regex.Pattern CHILDREN_AUDIENCE_PATTERN =
            java.util.regex.Pattern.compile(
                    "\\b(?:kids|kid|children|children s|child|child s|boys|boy|boy s|girls|girl|girl s|youth"
                            + "|toddler|toddlers|baby|babies)\\b");
    private static final java.util.regex.Pattern UNISEX_AUDIENCE_PATTERN =
            java.util.regex.Pattern.compile("\\b(?:unisex|gender neutral|all gender|all genders|everyone)\\b");

    private final ProductCatalogMetadataNormalizer metadataNormalizer;

    List<MerchantCatalogProductCandidate> filterCatalogProducts(
            String query,
            CatalogSearchContext context,
            CatalogSearchFilters filters,
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
        ProductAudience requiredAudience = requiredAudience(query, context);
        return productCandidates.stream()
                .filter(productCandidate -> matchesCatalogFilters(productCandidate, requiredAudience, context, filters))
                .toList();
    }

    List<MerchantSemanticProductResult> filterProductResults(
            String query,
            CatalogSearchContext context,
            CatalogSearchFilters filters,
            List<MerchantSemanticProductResult> products
    ) {
        ProductAudience requiredAudience = requiredAudience(query, context);
        return products.stream()
                .filter(product -> matchesProductFilters(product, requiredAudience, context, filters))
                .toList();
    }

    private boolean matchesCatalogFilters(
            MerchantCatalogProductCandidate productCandidate,
            ProductAudience requiredAudience,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        return matchesCategories(productCandidate, filters)
                && matchesAudience(catalogAudienceText(productCandidate), requiredAudience)
                && matchesPrice(
                productCandidate.priceMinAmount(),
                productCandidate.priceMaxAmount(),
                productCandidate.priceCurrency(),
                context,
                filters
        );
    }

    private boolean matchesProductFilters(
            MerchantSemanticProductResult product,
            ProductAudience requiredAudience,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        String currency = firstPresent(
                product.selectedVariantPriceCurrency(),
                product.detailPriceCurrency(),
                product.priceCurrency()
        );
        Long selectedVariantPrice = UcpDecimal.decimalAmountToMinor(product.selectedVariantPriceAmount(), currency);
        Long detailMin = UcpDecimal.decimalAmountToMinor(product.detailPriceMin(), currency);
        Long detailMax = UcpDecimal.decimalAmountToMinor(product.detailPriceMax(), currency);
        Long minAmount = firstPresent(selectedVariantPrice, detailMin, product.priceMinAmount());
        Long maxAmount = firstPresent(selectedVariantPrice, detailMax, product.priceMaxAmount(), minAmount);
        return matchesPrice(minAmount, maxAmount, currency, context, filters)
                && matchesAudience(productAudienceText(product), requiredAudience);
    }

    private ProductAudience requiredAudience(String query, CatalogSearchContext context) {
        String normalizedIntent = normalizedAudienceText(context == null ? null : context.intent());
        if (normalizedIntent.contains("hard apparel audience filter men s sizing")
                || normalizedIntent.contains("clothing fit signal prefer men s sizing")) {
            return ProductAudience.MEN;
        }
        if (normalizedIntent.contains("hard apparel audience filter women s sizing")
                || normalizedIntent.contains("clothing fit signal prefer women s sizing")) {
            return ProductAudience.WOMEN;
        }

        AudienceEvidence queryEvidence = audienceEvidence(query);
        if (queryEvidence.men() && !queryEvidence.women() && !queryEvidence.children()) {
            return ProductAudience.MEN;
        }
        if (queryEvidence.women() && !queryEvidence.men() && !queryEvidence.children()) {
            return ProductAudience.WOMEN;
        }
        return null;
    }

    private boolean matchesAudience(String audienceText, ProductAudience requiredAudience) {
        if (requiredAudience == null) {
            return true;
        }
        AudienceEvidence evidence = audienceEvidence(audienceText);
        if (!evidence.hasExplicitAudience() || evidence.unisex()) {
            return true;
        }
        return switch (requiredAudience) {
            case MEN -> evidence.men() || (!evidence.women() && !evidence.children());
            case WOMEN -> evidence.women() || (!evidence.men() && !evidence.children());
        };
    }

    private AudienceEvidence audienceEvidence(String value) {
        String normalized = normalizedAudienceText(value);
        if (normalized.isBlank()) {
            return new AudienceEvidence(false, false, false, false);
        }
        return new AudienceEvidence(
                MEN_AUDIENCE_PATTERN.matcher(normalized).find(),
                WOMEN_AUDIENCE_PATTERN.matcher(normalized).find(),
                CHILDREN_AUDIENCE_PATTERN.matcher(normalized).find(),
                UNISEX_AUDIENCE_PATTERN.matcher(normalized).find()
        );
    }

    private String catalogAudienceText(MerchantCatalogProductCandidate productCandidate) {
        CatalogSearchResponse.Product product = productCandidate.product();
        return joinedAudienceText(Stream.of(
                        Stream.of(product.title(), productCandidate.descriptionHtml()),
                        productCandidate.categoryValues().stream(),
                        productCandidate.tagValues().stream(),
                        metadataNormalizer.stringValues(product.materials()).stream(),
                        metadataNormalizer.stringValues(product.collections()).stream(),
                        metadataNormalizer.stringValues(product.metadata()).stream(),
                        metadataNormalizer.stringValues(product.metafields()).stream(),
                        metadataNormalizer.stringValues(product.techSpecs()).stream(),
                        safeNonNullList(product.variants()).stream()
                                .flatMap(variant -> Stream.of(
                                        variant.title(),
                                        variant.description() == null ? null : variant.description().html()
                                ))
                )
                .flatMap(stream -> stream));
    }

    private String productAudienceText(MerchantSemanticProductResult product) {
        return joinedAudienceText(Stream.of(
                        Stream.of(
                                product.title(),
                                product.detailDescription(),
                                product.descriptionHtml(),
                                product.selectedVariantTitle(),
                                product.selectedVariantImageAltText()
                        ),
                        safeList(product.categories()).stream()
                                .flatMap(category -> Stream.of(category.value(), category.taxonomy())),
                        safeList(product.media()).stream().map(ProductCatalogMedia::altText),
                        safeList(product.detailImages()).stream().map(ProductDetailsResponse.Image::altText),
                        safeList(product.detailOptions()).stream()
                                .flatMap(option -> Stream.concat(
                                        Stream.of(option.name()),
                                        safeList(option.values()).stream()
                                )),
                        safeList(product.selectedOptions()).stream()
                                .flatMap(option -> Stream.of(option.name(), option.value())),
                        safeList(product.materials()).stream(),
                        safeList(product.collections()).stream(),
                        safeList(product.attributes()).stream()
                                .flatMap(attribute -> Stream.of(attribute.name(), attribute.value()))
                )
                .flatMap(stream -> stream));
    }

    private String joinedAudienceText(Stream<String> values) {
        return values
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String normalizedAudienceText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = AUDIENCE_WORD_SEPARATOR_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return AUDIENCE_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    private boolean matchesCategories(
            MerchantCatalogProductCandidate productCandidate,
            CatalogSearchFilters filters
    ) {
        if (filters == null || safeList(filters.categories()).isEmpty()) {
            return true;
        }
        List<String> productCategories = Stream.concat(
                        productCandidate.categoryValues().stream(),
                        productCandidate.tagValues().stream()
                )
                .map(metadataNormalizer::normalizedValue)
                .filter(value -> !value.isBlank())
                .toList();
        return safeList(filters.categories()).stream()
                .map(metadataNormalizer::normalizedValue)
                .filter(value -> !value.isBlank())
                .anyMatch(category -> productCategories.stream()
                        .anyMatch(productCategory -> productCategory.equals(category)
                                || productCategory.contains(category)));
    }

    private boolean matchesPrice(
            Long minAmount,
            Long maxAmount,
            String currency,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        CatalogSearchPriceFilter price = filters == null ? null : filters.price();
        if (price == null || (price.min() == null && price.max() == null)) {
            return true;
        }
        String expectedCurrency = UcpDecimal.normalizedCurrency(context == null ? null : context.currency());
        String productCurrency = UcpDecimal.normalizedCurrency(currency);
        if (expectedCurrency != null && productCurrency != null && !expectedCurrency.equals(productCurrency)) {
            return false;
        }
        if (price.min() != null && !hasKnownPriceAtOrAbove(minAmount, maxAmount, price.min())) {
            return false;
        }
        return price.max() == null || hasKnownPriceAtOrBelow(minAmount, maxAmount, price.max());
    }

    private boolean hasKnownPriceAtOrAbove(Long minAmount, Long maxAmount, Long threshold) {
        if (maxAmount != null) {
            return maxAmount >= threshold;
        }
        return minAmount != null && minAmount >= threshold;
    }

    private boolean hasKnownPriceAtOrBelow(Long minAmount, Long maxAmount, Long threshold) {
        if (minAmount != null) {
            return minAmount <= threshold;
        }
        return maxAmount != null && maxAmount <= threshold;
    }

    private String firstPresent(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third;
    }

    private Long firstPresent(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        return second == null ? third : second;
    }

    private Long firstPresent(Long first, Long second, Long third, Long fourth) {
        Long value = firstPresent(first, second, third);
        return value == null ? fourth : value;
    }

    private enum ProductAudience {
        MEN,
        WOMEN
    }

    private record AudienceEvidence(
            boolean men,
            boolean women,
            boolean children,
            boolean unisex
    ) {

        boolean hasExplicitAudience() {
            return men || women || children || unisex;
        }
    }
}
