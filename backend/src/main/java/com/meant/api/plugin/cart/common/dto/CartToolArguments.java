package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CartToolArguments(
        @JsonProperty("line_items")
        List<LineItem> lineItems,
        Map<String, Object> buyer,
        Map<String, Object> context,
        Fulfillment fulfillment,
        Discounts discounts,
        String note
) {

    public static CartToolArguments create(
            List<CartAddItem> addItems,
            Map<String, Object> buyerIdentity,
            Map<String, Object> context,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
            List<String> discountCodes,
            String note
    ) {
        return new CartToolArguments(
                addLineItems(addItems),
                emptyToNull(buyerIdentity),
                emptyToNull(context),
                fulfillment(deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions),
                discounts(discountCodes),
                note
        );
    }

    public static CartToolArguments update(
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<CartUpdateItem> removeItems,
            Map<String, Object> buyerIdentity,
            Map<String, Object> context,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
            List<String> discountCodes,
            String note
    ) {
        List<LineItem> lineItems = new ArrayList<>(addLineItems(addItems));
        safeList(updateItems).stream()
                .filter(item -> item != null && hasText(item.id()))
                .map(item -> new LineItem(
                        item.id(),
                        item.quantity(),
                        hasText(item.productVariantId()) ? new Item(item.productVariantId()) : null
                ))
                .forEach(lineItems::add);
        safeList(removeItems).stream()
                .filter(item -> item != null && hasText(item.id()))
                .map(item -> new LineItem(
                        item.id(),
                        0,
                        hasText(item.productVariantId()) ? new Item(item.productVariantId()) : null
                ))
                .forEach(lineItems::add);

        return new CartToolArguments(
                lineItems,
                emptyToNull(buyerIdentity),
                emptyToNull(context),
                fulfillment(deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions),
                discounts(discountCodes),
                note
        );
    }

    private static List<LineItem> addLineItems(List<CartAddItem> addItems) {
        return safeList(addItems).stream()
                .map(CartToolArguments::requireVariant)
                .map(item -> new LineItem(null, item.quantity(), new Item(
                        item.productVariantId(),
                        item.productId(),
                        item.selectedOptions(),
                        item.components(),
                        item.sellingPlan()
                )))
                .toList();
    }

    private static CartAddItem requireVariant(CartAddItem item) {
        if (item == null || !hasText(item.productVariantId())) {
            throw new IllegalArgumentException("Cart add item requires an exact variant id");
        }
        return item;
    }

    private static Discounts discounts(List<String> discountCodes) {
        List<String> codes = safeList(discountCodes).stream()
                .filter(CartToolArguments::hasText)
                .toList();
        return codes.isEmpty() ? null : new Discounts(codes);
    }

    private static Fulfillment fulfillment(
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions
    ) {
        List<Map<String, Object>> destinations = safeList(deliveryAddressesToReplace).isEmpty()
                ? safeList(deliveryAddressesToAdd)
                : safeList(deliveryAddressesToReplace);
        List<Map<String, Object>> groups = safeList(selectedDeliveryOptions);
        if (destinations.isEmpty() && groups.isEmpty()) {
            return null;
        }

        Map<String, Object> method = new LinkedHashMap<>();
        method.put("type", "shipping");
        List<Map<String, Object>> mappedDestinations = destinations.stream()
                .map(CartToolArguments::destination)
                .filter(map -> !map.isEmpty())
                .toList();
        if (!mappedDestinations.isEmpty()) {
            method.put("destinations", mappedDestinations);
        }
        List<Map<String, Object>> mappedGroups = groups.stream()
                .map(CartToolArguments::fulfillmentGroup)
                .filter(map -> !map.isEmpty())
                .toList();
        if (!mappedGroups.isEmpty()) {
            method.put("groups", mappedGroups);
        }
        if (!method.containsKey("destinations") && !method.containsKey("groups")) {
            return null;
        }
        return new Fulfillment(List.of(method));
    }

    private static Map<String, Object> destination(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> address = mapValue(source.get("delivery_address"));
        if (address.isEmpty()) {
            address = source;
        }

        Map<String, Object> destination = new LinkedHashMap<>();
        put(destination, "id", firstValue(source, "id", "destination_id"));
        put(destination, "first_name", firstValue(address, "first_name", "firstName"));
        put(destination, "last_name", firstValue(address, "last_name", "lastName"));
        put(destination, "phone_number", firstValue(address, "phone_number", "phone"));
        put(destination, "street_address", firstValue(address, "street_address", "address1"));
        put(destination, "extended_address", firstValue(address, "extended_address", "address2"));
        put(destination, "address_locality", firstValue(address, "address_locality", "city"));
        put(destination, "address_region", firstValue(address, "address_region", "province_code", "province"));
        put(destination, "postal_code", firstValue(address, "postal_code", "zip"));
        put(destination, "address_country", firstValue(address, "address_country", "country_code", "country"));
        return destination;
    }

    private static Map<String, Object> fulfillmentGroup(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        put(group, "id", firstValue(source, "id", "group_id", "delivery_group_id"));
        put(group, "selected_option_id", firstValue(source, "selected_option_id", "option_handle", "delivery_option_handle"));
        return group;
    }

    private static Object firstValue(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void put(Map<String, Object> destination, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        destination.put(key, value);
    }

    private static Map<String, Object> emptyToNull(Map<String, Object> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null) {
                values.put(key.toString(), mapValue);
            }
        });
        return values;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LineItem(
            String id,
            Integer quantity,
            Item item
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Item(
            String id,
            @JsonProperty("product_id") String productId,
            @JsonProperty("selected_options") List<CartAddItem.SelectedOption> selectedOptions,
            List<CartAddItem.Component> components,
            @JsonProperty("selling_plan") CartAddItem.SellingPlan sellingPlan
    ) {
        public Item(String id) {
            this(id, null, List.of(), List.of(), null);
        }
    }

    public record Discounts(
            List<String> codes
    ) {
    }

    public record Fulfillment(
            List<Map<String, Object>> methods
    ) {
    }
}
