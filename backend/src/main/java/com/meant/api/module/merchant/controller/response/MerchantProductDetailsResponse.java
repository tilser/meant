package com.meant.api.module.merchant.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.module.merchant.controller.mapper.ProductDetailsJsonValueMapper.toJsonNode;

import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

public record MerchantProductDetailsResponse(
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
        MerchantProductMessageSanitizer.TransportContext buyerContext =
                MerchantProductMessageSanitizer.context(result);
        ProductDetailsResponse.Product product = result.product();
        if (product == null) {
            return new MerchantProductDetailsResponse(
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
                    messageResponses(result)
            );
        }
        ProductDetailsResponse.PriceRange priceRange = product.priceRange();
        ProductDetailsResponse.PriceRange listPriceRange = product.listPriceRange();
        ProductDetailsResponse.SelectedVariant selectedVariant = product.selectedOrFirstAvailableVariant();
        String priceCurrency = priceRange == null ? null : priceRange.currency();
        UcpMoney productListPrice = product.listPrice() == null
                ? null
                : UcpMoney.value(product.listPrice(), priceCurrency);
        UcpMoney selectedListPrice = selectedVariant == null || selectedVariant.listPrice() == null
                ? null
                : UcpMoney.value(selectedVariant.listPrice(), selectedVariant.currency());
        return new MerchantProductDetailsResponse(
                product.productId(),
                product.handle(),
                buyerText(product.title(), buyerContext),
                buyerText(product.description(), buyerContext),
                buyerUrl(product.url(), buyerContext),
                buyerUrl(product.imageUrl(), buyerContext),
                safeList(product.images()).stream()
                        .filter(Objects::nonNull)
                        .map(image -> ProductImageResponse.from(image, buyerContext))
                        .toList(),
                safeList(product.media()).stream()
                        .filter(Objects::nonNull)
                        .map(media -> ProductMediaResponse.from(media, buyerContext))
                        .toList(),
                safeList(product.categories()).stream()
                        .filter(Objects::nonNull)
                        .map(category -> ProductCategoryResponse.from(category, buyerContext))
                        .toList(),
                buyerTextValues(product.tags(), buyerContext),
                safeList(product.options()).stream()
                        .filter(Objects::nonNull)
                        .map(option -> ProductOptionResponse.from(option, buyerContext))
                        .toList(),
                safeList(product.variants()).stream()
                        .filter(Objects::nonNull)
                        .map(variant -> ProductVariantResponse.from(variant, buyerContext))
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
                selectedVariant == null ? null : buyerText(selectedVariant.title(), buyerContext),
                selectedVariant == null ? null : selectedVariant.price(),
                selectedVariant == null ? null : selectedVariant.currency(),
                selectedVariant == null ? null : buyerText(selectedVariant.sku(), buyerContext),
                moneyText(selectedListPrice),
                selectedListPrice == null ? null : selectedListPrice.currency(),
                selectedVariant == null ? null : buyerUrl(selectedVariant.imageUrl(), buyerContext),
                selectedVariant == null ? null : buyerText(selectedVariant.imageAltText(), buyerContext),
                selectedVariant == null ? null : selectedVariant.available(),
                selectedVariant == null
                        ? List.of()
                        : safeList(selectedVariant.selectedOptions()).stream()
                                .filter(Objects::nonNull)
                                .map(option -> ProductSelectedOptionResponse.from(option, buyerContext))
                                .toList(),
                buyerTextValues(stringValues(toJsonNode(product.skus())), buyerContext),
                buyerTextValues(stringValues(toJsonNode(product.certifications())), buyerContext),
                buyerTextValues(stringValues(toJsonNode(product.materials())), buyerContext),
                buyerTextValues(stringValues(toJsonNode(product.collections())), buyerContext),
                buyerAttributes(attributes(
                        toJsonNode(product.metadata()),
                        toJsonNode(product.metafields()),
                        toJsonNode(product.techSpecs())
                ), buyerContext),
                messageResponses(result)
        );
    }

    public record ProductImageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText
    ) {

        static ProductImageResponse from(
                ProductDetailsResponse.Image image,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new ProductImageResponse(
                    buyerUrl(image.url(), context),
                    buyerText(image.altText(), context)
            );
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

        static ProductMediaResponse from(
                ProductDetailsResponse.Media media,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new ProductMediaResponse(
                    MerchantProductMessageSanitizer.buyerSafeMediaType(media.type(), context),
                    buyerUrl(media.url(), context),
                    buyerText(media.altText(), context),
                    buyerUrl(media.previewImageUrl(), context)
            );
        }
    }

    public record ProductCategoryResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String taxonomy
    ) {

        static ProductCategoryResponse from(
                ProductDetailsResponse.Category category,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new ProductCategoryResponse(
                    buyerText(category.value(), context),
                    buyerText(category.taxonomy(), context)
            );
        }
    }

    public record ProductOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static ProductOptionResponse from(
                ProductDetailsResponse.Option option,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new ProductOptionResponse(
                    buyerText(option.name(), context),
                    buyerTextValues(option.values(), context)
            );
        }
    }

    public record ProductSelectedOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductSelectedOptionResponse from(
                ProductDetailsResponse.SelectedOption selectedOption,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new ProductSelectedOptionResponse(
                    buyerText(selectedOption.name(), context),
                    buyerText(selectedOption.value(), context)
            );
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

        static ProductVariantResponse from(
                ProductDetailsResponse.Variant variant,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            UcpMoney listPrice = variant.listPrice() == null
                    ? null
                    : UcpMoney.value(variant.listPrice(), variant.currency());
            return new ProductVariantResponse(
                    variant.variantId(),
                    variant.handle(),
                    buyerText(variant.title(), context),
                    buyerText(variant.description(), context),
                    buyerUrl(variant.url(), context),
                    variant.price(),
                    variant.currency(),
                    moneyText(listPrice),
                    listPrice == null ? null : listPrice.currency(),
                    buyerText(variant.sku(), context),
                    buyerUrl(variant.imageUrl(), context),
                    buyerText(variant.imageAltText(), context),
                    safeList(variant.media()).stream()
                            .filter(Objects::nonNull)
                            .map(media -> ProductMediaResponse.from(media, context))
                            .toList(),
                    variant.available(),
                    safeList(variant.selectedOptions()).stream()
                            .filter(Objects::nonNull)
                            .map(option -> ProductSelectedOptionResponse.from(option, context))
                            .toList(),
                    safeList(variant.categories()).stream()
                            .filter(Objects::nonNull)
                            .map(category -> ProductCategoryResponse.from(category, context))
                            .toList(),
                    buyerTextValues(variant.tags(), context),
                    buyerAttributes(
                            MerchantProductDetailsResponse.attributes(toJsonNode(variant.metadata())),
                            context
                    )
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

        static ProductMessageResponse from(
                ProductDetailsResponse.Message message,
                ProductDetailsResult result
        ) {
            MerchantProductMessageSanitizer.SanitizedMessage sanitized =
                    MerchantProductMessageSanitizer.sanitize(message, result);
            return new ProductMessageResponse(
                    sanitized.type(),
                    sanitized.code(),
                    sanitized.path(),
                    sanitized.contentType(),
                    sanitized.content(),
                    sanitized.severity(),
                    sanitized.presentation(),
                    sanitized.imageUrl(),
                    sanitized.url()
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

    private static String buyerText(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.sanitizeBuyerText(value, context);
    }

    private static String buyerUrl(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.buyerSafeUrl(value, context);
    }

    private static List<String> buyerTextValues(
            List<String> values,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return distinctStrings(safeList(values).stream()
                .map(value -> buyerText(value, context))
                .toList());
    }

    private static List<ProductAttributeResponse> buyerAttributes(
            List<ProductAttributeResponse> attributes,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return safeList(attributes).stream()
                .filter(Objects::nonNull)
                .map(attribute -> new ProductAttributeResponse(
                        buyerText(attribute.name(), context),
                        buyerText(attribute.value(), context)))
                .filter(attribute -> blankToNull(attribute.name()) != null
                        && blankToNull(attribute.value()) != null)
                .toList();
    }

    private static List<ProductMessageResponse> messageResponses(ProductDetailsResult result) {
        return safeList(result.messages()).stream()
                .filter(Objects::nonNull)
                .map(message -> ProductMessageResponse.from(message, result))
                .toList();
    }

    private static List<String> stringValues(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        List<ValueNode> stack = new ArrayList<>();
        stack.add(new ValueNode(value, 0));
        while (!stack.isEmpty()) {
            ValueNode node = stack.remove(stack.size() - 1);
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            if (node.value().isArray()) {
                List<JsonNode> items = new ArrayList<>(node.value().values());
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            if (node.value().isObject()) {
                JsonNode namedValue = firstField(
                        node.value(), "values", "value", "name", "label", "title", "text");
                if (namedValue != null) {
                    stack.add(new ValueNode(namedValue, node.depth() + 1));
                    continue;
                }
                List<JsonNode> mapValues = new ArrayList<>(node.value().values());
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

    private static List<ProductAttributeResponse> attributes(JsonNode... values) {
        Map<String, ProductAttributeResponse> attributes = new LinkedHashMap<>();
        for (JsonNode value : values) {
            collectAttributes(attributes, "metadata", value, 0);
        }
        return List.copyOf(attributes.values());
    }

    private static void collectAttributes(
            Map<String, ProductAttributeResponse> attributes,
            String name,
            JsonNode value,
            int depth
    ) {
        if (depth > MAX_METADATA_DEPTH || value == null || value.isNull() || value.isMissingNode()) {
            return;
        }
        if (value.isObject()) {
            JsonNode namedValue = firstField(value, "value", "values", "text", "description");
            JsonNode keyField = firstField(value, "name", "key", "label", "title");
            if (namedValue != null && keyField != null) {
                String namedKey = firstPresent(scalarString(keyField), name);
                addAttribute(attributes, namedKey, String.join(", ", stringValues(namedValue)));
                return;
            }
            value.properties().forEach(entry -> {
                String nestedName = blankToNull(entry.getKey());
                if (nestedName != null) {
                    collectAttributes(
                            attributes,
                            "metadata".equals(name) ? nestedName : name + " " + nestedName,
                            entry.getValue(),
                            depth + 1
                    );
                }
            });
            return;
        }
        if (value.isArray()) {
            addAttribute(attributes, name, String.join(", ", stringValues(value)));
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

    private static JsonNode firstField(JsonNode object, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String scalarString(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isString()) {
            return blankToNull(value.stringValue());
        }
        if (value.isNumber() || value.isBoolean()) {
            return blankToNull(value.asString());
        }
        return null;
    }

    private static String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValueNode(JsonNode value, int depth) {
    }
}
