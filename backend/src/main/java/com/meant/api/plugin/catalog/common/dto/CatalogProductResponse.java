package com.meant.api.plugin.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.support.UcpDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProductResponse(
        String id,
        String title,
        Description description,
        String url,
        String handle,
        @JsonProperty("price_range")
        @JsonAlias("priceRange")
        PriceRange priceRange,
        @JsonProperty("list_price_range")
        @JsonAlias({"listPriceRange", "compare_at_price_range", "compareAtPriceRange"})
        PriceRange listPriceRange,
        List<Variant> variants,
        @JsonProperty("total_variants")
        @JsonAlias("totalVariants")
        Integer totalVariants,
        List<Option> options,
        List<Media> media,
        List<Category> categories,
        List<String> tags,
        JsonNode metadata,
        @JsonProperty("gift_card")
        @JsonAlias("giftCard")
        Boolean giftCard,
        List<Collection> collections,
        List<SelectedOption> selected
) {

    public CatalogProductResponse(
            String id,
            String title,
            Description description,
            String url,
            String handle,
            PriceRange priceRange,
            PriceRange listPriceRange,
            List<Variant> variants,
            List<Option> options,
            List<Media> media,
            List<Category> categories,
            List<String> tags,
            JsonNode metadata,
            Boolean giftCard,
            List<Collection> collections,
            List<SelectedOption> selected
    ) {
        this(
                id,
                title,
                description,
                url,
                handle,
                priceRange,
                listPriceRange,
                variants,
                null,
                options,
                media,
                categories,
                tags,
                metadata,
                giftCard,
                collections,
                selected
        );
    }

    public ProductDetailsResponse.Product toProductDetailsProduct() {
        List<Media> safeMedia = safeList(media);
        List<Category> safeCategories = safeList(categories);
        List<Variant> safeVariants = safeList(variants);
        Variant selectedVariant = selectedVariant();
        return new ProductDetailsResponse.Product(
                id,
                handle,
                title,
                html(description),
                url,
                firstMediaUrl(safeMedia, selectedVariant == null ? List.of() : safeList(selectedVariant.media())),
                safeMedia.stream().filter(Objects::nonNull).map(Media::toImage).filter(Objects::nonNull).toList(),
                safeMedia.stream().filter(Objects::nonNull).map(Media::toDetailsMedia).filter(Objects::nonNull).toList(),
                safeCategories.stream().filter(Objects::nonNull).map(Category::toDetailsCategory).filter(Objects::nonNull).toList(),
                safeList(tags).stream().filter(value -> value != null && !value.isBlank()).distinct().toList(),
                safeList(options).stream().filter(Objects::nonNull).map(Option::toDetailsOption).filter(Objects::nonNull).toList(),
                safeList(selected).stream()
                        .filter(Objects::nonNull)
                        .map(SelectedOption::toDetailsSelectedOption)
                        .filter(Objects::nonNull)
                        .toList(),
                safeVariants.stream().filter(Objects::nonNull).map(variant -> variant.toDetailsVariant(selected)).filter(Objects::nonNull).toList(),
                totalVariants,
                priceRange == null ? null : priceRange.toDetailsPriceRange(),
                listPriceRange == null ? null : listPriceRange.toDetailsPriceRange(),
                detailsMoney(moneyOrRange(listPriceRange)),
                null,
                null,
                selectedVariant == null || selectedVariant.requires() == null
                        ? null
                        : selectedVariant.requires().sellingPlan(),
                List.of(),
                safeList(variants).stream()
                        .filter(Objects::nonNull)
                        .map(Variant::sku)
                        .filter(value -> value != null && !value.isBlank())
                        .toList(),
                null,
                null,
                collectionLabels(),
                metadata,
                null,
                null,
                selectedVariant == null ? null : selectedVariant.toDetailsSelectedVariant(selected)
        );
    }

    private Variant selectedVariant() {
        List<String> effectiveSelection = selectedOptionKeys(selected);
        if (!effectiveSelection.isEmpty()) {
            Variant effective = safeList(variants).stream()
                    .filter(Objects::nonNull)
                    .filter(variant -> selectedOptionKeys(variant.options()).equals(effectiveSelection))
                    .findFirst()
                    .orElse(null);
            if (effective != null) {
                return effective;
            }
        }
        return safeList(variants).stream()
                .filter(variant -> variant != null
                        && variant.availability() != null
                        && Boolean.TRUE.equals(variant.availability().available()))
                .findFirst()
                .orElseGet(() -> safeList(variants).stream().filter(Objects::nonNull).findFirst().orElse(null));
    }

    private static List<String> selectedOptionKeys(List<SelectedOption> options) {
        return safeList(options).stream()
                .filter(Objects::nonNull)
                .map(CatalogProductResponse::selectedOptionKey)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private static String selectedOptionKey(SelectedOption option) {
        String name = option.name();
        String value = firstText(option.label(), option.value());
        return name == null || name.isBlank() || value == null || value.isBlank()
                ? null
                : name + "\u0000" + value;
    }

    private List<String> collectionLabels() {
        return safeList(collections).stream()
                .flatMap(collection -> Stream.of(collection.title(), collection.handle()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static Money moneyOrRange(PriceRange priceRange) {
        if (priceRange == null) {
            return null;
        }
        if (priceRange.min() != null) {
            return priceRange.min();
        }
        return priceRange.max();
    }

    private static String firstMediaUrl(List<Media> productMedia, List<Media> variantMedia) {
        return List.of(productMedia, variantMedia).stream()
                .flatMap(List::stream)
                .map(media -> firstText(media.url(), media.previewImageUrl()))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String html(Description description) {
        return description == null ? null : description.html();
    }

    private static String amount(Money money) {
        if (money == null || money.amount() == null) {
            return null;
        }
        return UcpDecimal.minorAmountToDecimalText(money.amount(), money.currency());
    }

    private static ProductDetailsResponse.Money detailsMoney(Money money) {
        return money == null ? null : new ProductDetailsResponse.Money(money.amount(), money.currency());
    }

    private static String currency(Money first, Money second) {
        if (first != null && first.currency() != null && !first.currency().isBlank()) {
            return first.currency();
        }
        return second == null ? null : second.currency();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(
            String html
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(
            Money min,
            Money max
    ) {

        ProductDetailsResponse.PriceRange toDetailsPriceRange() {
            return new ProductDetailsResponse.PriceRange(amount(min), amount(max), currency(min, max));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonDeserialize(using = CatalogProductMoneyDeserializer.class)
    public record Money(
            Long amount,
            @JsonAlias({"currency_code", "currencyCode"})
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            String id,
            String sku,
            String handle,
            String title,
            Description description,
            String url,
            Money price,
            @JsonProperty("list_price")
            @JsonAlias({
                    "listPrice",
                    "compare_at_price",
                    "compareAtPrice",
                    "original_price",
                    "regular_price",
                    "was_price"
            })
            Money listPrice,
            Availability availability,
            List<SelectedOption> options,
            List<Media> media,
            Requires requires,
            List<Category> categories,
            List<String> tags,
            JsonNode metadata,
            @JsonProperty("checkout_url")
            @JsonAlias("checkoutUrl")
            String checkoutUrl
    ) {

        ProductDetailsResponse.SelectedVariant toDetailsSelectedVariant(List<SelectedOption> selected) {
            List<Media> safeMedia = safeList(media);
            return new ProductDetailsResponse.SelectedVariant(
                    id,
                    title,
                    amount(price),
                    price == null ? null : price.currency(),
                    sku,
                    detailsMoney(listPrice),
                    firstMediaUrl(safeMedia, List.of()),
                    safeMedia.stream()
                            .filter(Objects::nonNull)
                            .map(Media::altText)
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst()
                            .orElse(null),
                    safeMedia.stream().filter(Objects::nonNull).map(Media::toDetailsMedia).filter(Objects::nonNull).toList(),
                    availability == null ? null : availability.available(),
                    selectedOptions(selected)
            );
        }

        ProductDetailsResponse.Variant toDetailsVariant(List<SelectedOption> selected) {
            if (id == null && title == null && price == null && sku == null) {
                return null;
            }
            List<Media> safeMedia = safeList(media);
            String imageUrl = firstMediaUrl(safeMedia, List.of());
            return new ProductDetailsResponse.Variant(
                    id,
                    handle,
                    title,
                    html(description),
                    url,
                    amount(price),
                    price == null ? null : price.currency(),
                    sku,
                    detailsMoney(listPrice),
                    imageUrl,
                    safeMedia.stream()
                            .filter(Objects::nonNull)
                            .map(Media::altText)
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst()
                            .orElse(null),
                    safeMedia.stream().filter(Objects::nonNull).map(Media::toDetailsMedia).filter(Objects::nonNull).toList(),
                    availability == null ? null : availability.available(),
                    selectedOptions(selected),
                    safeList(categories).stream().filter(Objects::nonNull).map(Category::toDetailsCategory).filter(Objects::nonNull).toList(),
                    safeList(tags).stream().filter(value -> value != null && !value.isBlank()).distinct().toList(),
                    metadata
            );
        }

        private List<ProductDetailsResponse.SelectedOption> selectedOptions(List<SelectedOption> selected) {
            List<SelectedOption> values = safeList(options).isEmpty() ? safeList(selected) : safeList(options);
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(SelectedOption::toDetailsSelectedOption)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Availability(
            Boolean available
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requires(
            Boolean shipping,
            @JsonProperty("selling_plan")
            @JsonAlias("sellingPlan")
            Boolean sellingPlan
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            String name,
            List<OptionValue> values
    ) {

        ProductDetailsResponse.Option toDetailsOption() {
            List<ProductDetailsResponse.OptionValue> details = safeList(values).stream()
                    .filter(Objects::nonNull)
                    .map(OptionValue::toDetailsOptionValue)
                    .filter(Objects::nonNull)
                    .toList();
            List<String> labels = details.stream().map(ProductDetailsResponse.OptionValue::value).toList();
            return name == null && labels.isEmpty()
                    ? null
                    : new ProductDetailsResponse.Option(name, labels, details);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionValue(
            String label,
            String value,
            String name,
            Boolean available,
            Boolean exists
    ) {

        public OptionValue(String label, String value, String name) {
            this(label, value, name, null, null);
        }

        String labelValue() {
            return firstText(firstText(label, value), name);
        }

        ProductDetailsResponse.OptionValue toDetailsOptionValue() {
            String resolved = labelValue();
            return resolved == null || resolved.isBlank()
                    ? null
                    : new ProductDetailsResponse.OptionValue(resolved, available, exists);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedOption(
            String name,
            String label,
            String value
    ) {

        ProductDetailsResponse.SelectedOption toDetailsSelectedOption() {
            String selectedValue = firstText(label, value);
            return name == null && selectedValue == null
                    ? null
                    : new ProductDetailsResponse.SelectedOption(name, selectedValue);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            String type,
            String url,
            @JsonProperty("alt_text")
            @JsonAlias("altText")
            String altText,
            @JsonProperty("preview_image_url")
            @JsonAlias("previewImageUrl")
            String previewImageUrl
    ) {

        ProductDetailsResponse.Image toImage() {
            String imageUrl = firstText(url, previewImageUrl);
            return imageUrl == null ? null : new ProductDetailsResponse.Image(imageUrl, altText);
        }

        ProductDetailsResponse.Media toDetailsMedia() {
            return url == null && previewImageUrl == null
                    ? null
                    : new ProductDetailsResponse.Media(type, url, altText, previewImageUrl);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String value,
            String taxonomy
    ) {

        ProductDetailsResponse.Category toDetailsCategory() {
            return value == null && taxonomy == null ? null : new ProductDetailsResponse.Category(value, taxonomy);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Collection(
            String id,
            String handle,
            String title,
            Description description,
            String url
    ) {
    }
}
