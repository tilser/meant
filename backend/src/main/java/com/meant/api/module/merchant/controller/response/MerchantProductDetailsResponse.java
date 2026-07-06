package com.meant.api.module.merchant.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record MerchantProductDetailsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String endpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String handle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductImageResponse> images,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductMediaResponse> media,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductCategoryResponse> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> tags,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductOptionResponse> options,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductVariantResponse> variants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalVariants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceMax,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listPriceMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listPriceMax,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean requiresSellingPlan,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantPriceAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantSku,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantListPriceAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantListPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantImageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantImageAltText,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean selectedVariantAvailable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductSelectedOptionResponse> selectedOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> skus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> certifications,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> materials,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> collections,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductAttributeResponse> attributes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductMessageResponse> messages
) {

    private static final int MAX_METADATA_DEPTH = 32;

    public static MerchantProductDetailsResponse from(ProductDetailsResult result) {
        ProductDetailsResponse.Product product = result.product();
        if (product == null) {
            return new MerchantProductDetailsResponse(
                    result.endpoint(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    messageResponses(result.messages())
            );
        }
        ProductDetailsResponse.PriceRange priceRange = product.priceRange();
        ProductDetailsResponse.PriceRange listPriceRange = product.listPriceRange();
        ProductDetailsResponse.SelectedVariant selectedVariant = product.selectedOrFirstAvailableVariant();
        String priceCurrency = priceRange == null ? null : priceRange.currency();
        UcpMoney productListPrice = UcpMoney.value(product.listPrice(), priceCurrency);
        UcpMoney selectedListPrice = selectedVariant == null
                ? null
                : UcpMoney.value(selectedVariant.listPrice(), selectedVariant.currency());
        return new MerchantProductDetailsResponse(
                result.endpoint(),
                product.productId(),
                product.handle(),
                product.title(),
                product.description(),
                product.url(),
                product.imageUrl(),
                safeList(product.images()).stream()
                        .filter(Objects::nonNull)
                        .map(ProductImageResponse::from)
                        .toList(),
                safeList(product.media()).stream()
                        .filter(Objects::nonNull)
                        .map(ProductMediaResponse::from)
                        .toList(),
                safeList(product.categories()).stream()
                        .filter(Objects::nonNull)
                        .map(ProductCategoryResponse::from)
                        .toList(),
                distinctStrings(product.tags()),
                safeList(product.options()).stream()
                        .filter(Objects::nonNull)
                        .map(ProductOptionResponse::from)
                        .toList(),
                safeList(product.variants()).stream()
                        .filter(Objects::nonNull)
                        .map(ProductVariantResponse::from)
                        .toList(),
                product.totalVariants(),
                priceRange == null ? null : priceRange.min(),
                priceRange == null ? null : priceRange.max(),
                priceCurrency,
                listPriceRange == null ? moneyText(productListPrice) : listPriceRange.min(),
                listPriceRange == null ? moneyText(productListPrice) : listPriceRange.max(),
                listPriceRange == null ? productListPrice == null ? null : productListPrice.currency() : listPriceRange.currency(),
                product.requiresSellingPlan(),
                selectedVariant == null ? null : selectedVariant.variantId(),
                selectedVariant == null ? null : selectedVariant.title(),
                selectedVariant == null ? null : selectedVariant.price(),
                selectedVariant == null ? null : selectedVariant.currency(),
                selectedVariant == null ? null : selectedVariant.sku(),
                moneyText(selectedListPrice),
                selectedListPrice == null ? null : selectedListPrice.currency(),
                selectedVariant == null ? null : selectedVariant.imageUrl(),
                selectedVariant == null ? null : selectedVariant.imageAltText(),
                selectedVariant == null ? null : selectedVariant.available(),
                selectedVariant == null
                        ? List.of()
                        : safeList(selectedVariant.selectedOptions()).stream()
                                .filter(Objects::nonNull)
                                .map(ProductSelectedOptionResponse::from)
                                .toList(),
                stringValues(product.skus()),
                stringValues(product.certifications()),
                stringValues(product.materials()),
                stringValues(product.collections()),
                attributes(product.metadata(), product.metafields(), product.techSpecs()),
                messageResponses(result.messages())
        );
    }

    public record ProductImageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText
    ) {

        static ProductImageResponse from(ProductDetailsResponse.Image image) {
            return new ProductImageResponse(image.url(), image.altText());
        }
    }

    public record ProductMediaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String previewImageUrl
    ) {

        static ProductMediaResponse from(ProductDetailsResponse.Media media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText(), media.previewImageUrl());
        }
    }

    public record ProductCategoryResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String taxonomy
    ) {

        static ProductCategoryResponse from(ProductDetailsResponse.Category category) {
            return new ProductCategoryResponse(category.value(), category.taxonomy());
        }
    }

    public record ProductOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static ProductOptionResponse from(ProductDetailsResponse.Option option) {
            return new ProductOptionResponse(option.name(), distinctStrings(option.values()));
        }
    }

    public record ProductSelectedOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductSelectedOptionResponse from(ProductDetailsResponse.SelectedOption selectedOption) {
            return new ProductSelectedOptionResponse(selectedOption.name(), selectedOption.value());
        }
    }

    public record ProductVariantResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String variantId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String handle,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String priceAmount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String priceCurrency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String listPriceAmount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String listPriceCurrency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String sku,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String imageAltText,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductMediaResponse> media,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Boolean available,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductSelectedOptionResponse> selectedOptions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductCategoryResponse> categories,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> tags,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ProductAttributeResponse> attributes
    ) {

        static ProductVariantResponse from(ProductDetailsResponse.Variant variant) {
            UcpMoney listPrice = UcpMoney.value(variant.listPrice(), variant.currency());
            return new ProductVariantResponse(
                    variant.variantId(),
                    variant.handle(),
                    variant.title(),
                    variant.description(),
                    variant.url(),
                    variant.price(),
                    variant.currency(),
                    moneyText(listPrice),
                    listPrice == null ? null : listPrice.currency(),
                    variant.sku(),
                    variant.imageUrl(),
                    variant.imageAltText(),
                    safeList(variant.media()).stream()
                            .filter(Objects::nonNull)
                            .map(ProductMediaResponse::from)
                            .toList(),
                    variant.available(),
                    safeList(variant.selectedOptions()).stream()
                            .filter(Objects::nonNull)
                            .map(ProductSelectedOptionResponse::from)
                            .toList(),
                    safeList(variant.categories()).stream()
                            .filter(Objects::nonNull)
                            .map(ProductCategoryResponse::from)
                            .toList(),
                    distinctStrings(variant.tags()),
                    MerchantProductDetailsResponse.attributes(variant.metadata())
            );
        }
    }

    public record ProductAttributeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {
    }

    public record ProductMessageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String path,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String contentType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String content,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String severity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String presentation,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url
    ) {

        static ProductMessageResponse from(ProductDetailsResponse.Message message) {
            return new ProductMessageResponse(
                    message.type(),
                    message.code(),
                    message.path(),
                    message.contentType(),
                    message.content(),
                    message.severity(),
                    message.presentation(),
                    message.imageUrl(),
                    message.url()
            );
        }
    }

    private static String moneyText(UcpMoney money) {
        return money == null ? null : UcpDecimal.minorAmountToDecimalText(money.amount(), money.currency());
    }

    private static List<String> distinctStrings(List<String> values) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (String value : safeList(values)) {
            String normalizedValue = blankToNull(value);
            if (normalizedValue != null) {
                seen.putIfAbsent(normalizedValue.toLowerCase(Locale.ROOT), normalizedValue);
            }
        }
        return List.copyOf(seen.values());
    }

    private static List<ProductMessageResponse> messageResponses(List<ProductDetailsResponse.Message> messages) {
        return safeList(messages).stream()
                .filter(Objects::nonNull)
                .map(ProductMessageResponse::from)
                .toList();
    }

    private static List<String> stringValues(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        List<ValueNode> stack = new ArrayList<>();
        stack.add(new ValueNode(value, 0));
        while (!stack.isEmpty()) {
            ValueNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            if (node.value() instanceof Collection<?> collection) {
                List<?> items = new ArrayList<>(collection);
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            if (node.value() instanceof Map<?, ?> map) {
                Object namedValue = firstMapValue(map, "values", "value", "name", "label", "title", "text");
                if (namedValue != null) {
                    stack.add(new ValueNode(namedValue, node.depth() + 1));
                    continue;
                }
                List<?> mapValues = new ArrayList<>(map.values());
                for (int index = mapValues.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(mapValues.get(index), node.depth() + 1));
                }
                continue;
            }
            String scalar = scalarString(node.value());
            if (scalar != null) {
                values.add(scalar);
            }
        }
        return distinctStrings(values);
    }

    private static List<ProductAttributeResponse> attributes(Object... values) {
        Map<String, ProductAttributeResponse> attributes = new LinkedHashMap<>();
        for (Object value : values) {
            collectAttributes(attributes, "metadata", value, 0);
        }
        return List.copyOf(attributes.values());
    }

    private static void collectAttributes(
            Map<String, ProductAttributeResponse> attributes,
            String name,
            Object value,
            int depth
    ) {
        if (depth > MAX_METADATA_DEPTH || value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object namedValue = firstMapValue(map, "value", "values", "text", "description");
            Object keyField = firstMapValue(map, "name", "key", "label", "title");
            if (namedValue != null && keyField != null) {
                String namedKey = firstPresent(scalarString(keyField), name);
                addAttribute(attributes, namedKey, String.join(", ", stringValues(namedValue)));
                return;
            }
            map.forEach((key, nestedValue) -> {
                String nestedName = scalarString(key);
                if (nestedName != null) {
                    collectAttributes(
                            attributes,
                            "metadata".equals(name) ? nestedName : name + " " + nestedName,
                            nestedValue,
                            depth + 1
                    );
                }
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            addAttribute(attributes, name, String.join(", ", stringValues(collection)));
            return;
        }
        addAttribute(attributes, name, scalarString(value));
    }

    private static void addAttribute(
            Map<String, ProductAttributeResponse> attributes,
            String name,
            String value
    ) {
        String normalizedName = blankToNull(name);
        String normalizedValue = blankToNull(value);
        if (normalizedName == null || normalizedValue == null) {
            return;
        }
        attributes.putIfAbsent(
                normalizedName.toLowerCase(Locale.ROOT) + "|" + normalizedValue.toLowerCase(Locale.ROOT),
                new ProductAttributeResponse(normalizedName, normalizedValue)
        );
    }

    private static Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return blankToNull(string);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return blankToNull(value.toString());
        }
        return null;
    }

    private static String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValueNode(Object value, int depth) {
    }
}
