package com.meant.api.plugin.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        List<Option> options,
        List<Media> media,
        List<Category> categories,
        @JsonProperty("gift_card")
        @JsonAlias("giftCard")
        Boolean giftCard,
        List<Collection> collections,
        List<SelectedOption> selected
) {

    public ProductDetailsResponse.Product toProductDetailsProduct() {
        List<Media> safeMedia = safeList(media);
        Variant selectedVariant = selectedVariant();
        return new ProductDetailsResponse.Product(
                id,
                title,
                html(description),
                url,
                firstMediaUrl(safeMedia, selectedVariant == null ? List.of() : safeList(selectedVariant.media())),
                safeMedia.stream().map(Media::toImage).filter(Objects::nonNull).toList(),
                safeMedia.stream().map(Media::toDetailsMedia).filter(Objects::nonNull).toList(),
                safeList(options).stream().map(Option::toDetailsOption).filter(Objects::nonNull).toList(),
                safeList(variants).isEmpty() ? null : safeList(variants).size(),
                priceRange == null ? null : priceRange.toDetailsPriceRange(),
                moneyOrRange(listPriceRange),
                null,
                null,
                selectedVariant == null || selectedVariant.requires() == null
                        ? null
                        : selectedVariant.requires().sellingPlan(),
                List.of(),
                safeList(variants).stream()
                        .map(Variant::sku)
                        .filter(value -> value != null && !value.isBlank())
                        .toList(),
                null,
                null,
                collectionLabels(),
                categoryLabels(),
                null,
                null,
                selectedVariant == null ? null : selectedVariant.toDetailsSelectedVariant(selected)
        );
    }

    private Variant selectedVariant() {
        return safeList(variants).stream()
                .filter(variant -> variant != null
                        && variant.availability() != null
                        && Boolean.TRUE.equals(variant.availability().available()))
                .findFirst()
                .orElseGet(() -> safeList(variants).stream().filter(Objects::nonNull).findFirst().orElse(null));
    }

    private List<String> collectionLabels() {
        return safeList(collections).stream()
                .flatMap(collection -> Stream.of(collection.title(), collection.handle()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> categoryLabels() {
        return safeList(categories).stream()
                .flatMap(category -> Stream.of(category.value(), category.taxonomy()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private static Object moneyOrRange(PriceRange priceRange) {
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
        Object amount = money.amount();
        Long minorAmount = explicitMinorAmount(amount);
        if (minorAmount == null && (amount instanceof Number || amount instanceof CharSequence)) {
            minorAmount = UcpMoney.minorAmount(amount.toString(), money.currency());
        }
        return UcpDecimal.minorAmountToDecimalText(minorAmount, money.currency());
    }

    private static Long explicitMinorAmount(Object amount) {
        Long wholeNumber = wholeNumber(amount);
        if (wholeNumber != null) {
            return wholeNumber;
        }
        if (amount instanceof CharSequence value && value.toString().trim().matches("-?\\d+")) {
            return UcpMoney.wholeNumberAmount(amount);
        }
        return null;
    }

    private static Long wholeNumber(Object amount) {
        if (amount instanceof Long value) {
            return value;
        }
        if (amount instanceof Integer value) {
            return value.longValue();
        }
        if (amount instanceof Number value) {
            return wholeNumber(value);
        }
        return null;
    }

    private static Long wholeNumber(Number value) {
        try {
            if (value instanceof BigInteger bigInteger) {
                return bigInteger.longValueExact();
            }
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
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
    public record Money(
            Object amount,
            @JsonAlias({"currency_code", "currencyCode"})
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            String id,
            String sku,
            String title,
            Description description,
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
                    listPrice,
                    firstMediaUrl(safeMedia, List.of()),
                    safeMedia.stream()
                            .map(Media::altText)
                            .filter(value -> value != null && !value.isBlank())
                            .findFirst()
                            .orElse(null),
                    safeMedia.stream().map(Media::toDetailsMedia).filter(Objects::nonNull).toList(),
                    availability == null ? null : availability.available(),
                    selectedOptions(selected)
            );
        }

        private List<ProductDetailsResponse.SelectedOption> selectedOptions(List<SelectedOption> selected) {
            List<SelectedOption> values = safeList(options).isEmpty() ? safeList(selected) : safeList(options);
            return values.stream()
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
            List<String> labels = safeList(values).stream()
                    .map(OptionValue::labelValue)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            return name == null && labels.isEmpty() ? null : new ProductDetailsResponse.Option(name, labels);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionValue(
            String label,
            String value,
            String name
    ) {

        String labelValue() {
            return firstText(firstText(label, value), name);
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
