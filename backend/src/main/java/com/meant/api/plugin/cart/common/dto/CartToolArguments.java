package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.support.UcpAttribution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CartToolArguments(
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @JsonProperty("line_items")
        List<LineItem> lineItems,
        CartBuyer buyer,
        CartContext context,
        UcpAttribution attribution,
        CartSignals signals,
        Fulfillment fulfillment,
        Discounts discounts,
        @JsonProperty("gift_card_codes")
        List<String> giftCardCodes,
        String note
) {

    public static CartToolArguments create(
            List<CartAddItem> addItems,
            CartBuyer buyerIdentity,
            CartContext context,
            CartSignals signals,
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        return new CartToolArguments(
                addLineItems(addItems),
                emptyToNull(buyerIdentity),
                emptyToNull(context),
                null,
                emptyToNull(signals),
                fulfillment(deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions),
                nonEmptyDiscounts(discountCodes),
                codes(giftCardCodes),
                note
        );
    }

    public static CartToolArguments update(
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<CartUpdateItem> removeItems,
            CartBuyer buyerIdentity,
            CartContext context,
            CartSignals signals,
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
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
                null,
                emptyToNull(signals),
                fulfillment(deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions),
                replacementDiscounts(discountCodes),
                codes(giftCardCodes),
                note
        );
    }

    public static CartToolArguments replacement(
            List<CartAddItem> lineItems,
            CartBuyer buyer,
            CartContext context,
            CartSignals signals,
            Fulfillment fulfillment,
            Discounts discounts,
            List<String> giftCardCodes,
            String note
    ) {
        return new CartToolArguments(
                addLineItems(lineItems), emptyToNull(buyer), emptyToNull(context), null,
                emptyToNull(signals), fulfillment, discounts, codes(giftCardCodes), note);
    }

    public CartToolArguments withAttribution(UcpAttribution value) {
        return new CartToolArguments(
                lineItems, buyer, context, value, signals, fulfillment, discounts, giftCardCodes, note);
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

    private static Discounts nonEmptyDiscounts(List<String> discountCodes) {
        List<String> codes = codes(discountCodes);
        return codes.isEmpty() ? null : new Discounts(codes);
    }

    private static Discounts replacementDiscounts(List<String> discountCodes) {
        return discountCodes == null ? null : new Discounts(codes(discountCodes));
    }

    private static List<String> codes(List<String> values) {
        return safeList(values).stream()
                .filter(CartToolArguments::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public static Fulfillment fulfillment(
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions
    ) {
        List<CartDeliveryAddressSelection> destinations = safeList(deliveryAddressesToReplace).isEmpty()
                ? safeList(deliveryAddressesToAdd)
                : safeList(deliveryAddressesToReplace);
        List<CartDeliveryOptionSelection> groups = safeList(selectedDeliveryOptions);
        if (destinations.isEmpty() && groups.isEmpty()) {
            return null;
        }

        List<String> methodIds = new ArrayList<>();
        destinations.stream().filter(Objects::nonNull).map(CartDeliveryAddressSelection::methodId)
                .filter(CartToolArguments::hasText).map(String::trim).forEach(methodIds::add);
        groups.stream().filter(Objects::nonNull).map(CartDeliveryOptionSelection::methodId)
                .filter(CartToolArguments::hasText).map(String::trim).forEach(methodIds::add);
        List<String> distinctMethodIds = methodIds.stream().distinct().toList();
        if (distinctMethodIds.isEmpty()) {
            distinctMethodIds = List.of("");
        }
        List<FulfillmentMethod> methods = distinctMethodIds.stream()
                .map(methodId -> fulfillmentMethod(methodId, destinations, groups))
                .filter(Objects::nonNull)
                .toList();
        if (methods.isEmpty()) {
            return null;
        }
        return new Fulfillment(methods);
    }

    private static FulfillmentMethod fulfillmentMethod(
            String methodId,
            List<CartDeliveryAddressSelection> addresses,
            List<CartDeliveryOptionSelection> selections
    ) {
        List<CartDeliveryAddressSelection> methodAddresses = addresses.stream()
                .filter(Objects::nonNull)
                .filter(value -> appliesToMethod(value.methodId(), methodId))
                .filter(value -> value.address() != null && !value.address().empty())
                .toList();
        List<CartDeliveryAddress> mappedDestinations = methodAddresses.stream()
                .map(CartDeliveryAddressSelection::address)
                .toList();
        List<FulfillmentGroup> mappedGroups = selections.stream()
                .filter(Objects::nonNull)
                .filter(value -> appliesToMethod(value.methodId(), methodId))
                .filter(value -> hasText(value.groupId()) && hasText(value.selectedOptionId()))
                .map(value -> new FulfillmentGroup(
                        value.groupId().trim(), List.of(), List.of(), value.selectedOptionId().trim()))
                .toList();
        if (mappedDestinations.isEmpty() && mappedGroups.isEmpty()) {
            return null;
        }
        String selectedDestinationId = methodAddresses.stream()
                .filter(value -> Boolean.TRUE.equals(value.selected()))
                .map(CartDeliveryAddressSelection::address)
                .map(CartDeliveryAddress::id)
                .filter(CartToolArguments::hasText)
                .findFirst()
                .orElse(null);
        return new FulfillmentMethod(
                hasText(methodId) ? methodId : null,
                "shipping",
                List.of(),
                mappedDestinations,
                selectedDestinationId,
                mappedGroups
        );
    }

    private static boolean appliesToMethod(String candidate, String methodId) {
        return !hasText(candidate) || !hasText(methodId) || candidate.trim().equals(methodId);
    }

    private static CartContext emptyToNull(CartContext value) {
        return value == null || value.empty() ? null : value;
    }

    private static CartBuyer emptyToNull(CartBuyer value) {
        return value == null || value.empty() ? null : value;
    }

    private static CartSignals emptyToNull(CartSignals value) {
        return value == null || value.empty() ? null : value;
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
            @JsonInclude(JsonInclude.Include.ALWAYS)
            List<String> codes
    ) {
        public Discounts {
            codes = codes == null ? List.of() : List.copyOf(codes);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Fulfillment(
            List<FulfillmentMethod> methods,
            @JsonIgnore Map<String, JsonNode> extensions
    ) {
        public Fulfillment(List<FulfillmentMethod> methods) {
            this(methods, null);
        }

        public Fulfillment {
            methods = methods == null ? List.of() : List.copyOf(methods);
            extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
        }

        @JsonAnySetter
        public void putExtension(String name, JsonNode value) {
            extensions.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> extensionValues() {
            return extensions;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentMethod(
            String id,
            String type,
            @JsonProperty("line_item_ids") List<String> lineItemIds,
            List<CartDeliveryAddress> destinations,
            @JsonProperty("selected_destination_id") String selectedDestinationId,
            List<FulfillmentGroup> groups,
            @JsonIgnore Map<String, JsonNode> extensions
    ) {
        public FulfillmentMethod(
                String id, String type, List<String> lineItemIds, List<CartDeliveryAddress> destinations,
                String selectedDestinationId, List<FulfillmentGroup> groups
        ) {
            this(id, type, lineItemIds, destinations, selectedDestinationId, groups, null);
        }

        public FulfillmentMethod {
            lineItemIds = lineItemIds == null ? List.of() : List.copyOf(lineItemIds);
            destinations = destinations == null ? List.of() : List.copyOf(destinations);
            groups = groups == null ? List.of() : List.copyOf(groups);
            extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
        }

        @JsonAnySetter
        public void putExtension(String name, JsonNode value) {
            extensions.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> extensionValues() {
            return extensions;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentGroup(
            String id,
            @JsonProperty("line_item_ids") List<String> lineItemIds,
            List<FulfillmentOption> options,
            @JsonProperty("selected_option_id") String selectedOptionId,
            @JsonIgnore Map<String, JsonNode> extensions
    ) {
        public FulfillmentGroup(
                String id, List<String> lineItemIds, List<FulfillmentOption> options, String selectedOptionId
        ) {
            this(id, lineItemIds, options, selectedOptionId, null);
        }

        public FulfillmentGroup {
            lineItemIds = lineItemIds == null ? List.of() : List.copyOf(lineItemIds);
            options = options == null ? List.of() : List.copyOf(options);
            extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
        }

        @JsonAnySetter
        public void putExtension(String name, JsonNode value) {
            extensions.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> extensionValues() {
            return extensions;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentOption(
            String id,
            String title,
            String description,
            String carrier,
            @JsonProperty("earliest_fulfillment_time") String earliestFulfillmentTime,
            @JsonProperty("latest_fulfillment_time") String latestFulfillmentTime,
            List<FulfillmentTotal> totals,
            @JsonIgnore Map<String, JsonNode> extensions
    ) {
        public FulfillmentOption(
                String id, String title, String description, String carrier, String earliestFulfillmentTime,
                String latestFulfillmentTime, List<FulfillmentTotal> totals
        ) {
            this(id, title, description, carrier, earliestFulfillmentTime, latestFulfillmentTime, totals, null);
        }

        public FulfillmentOption {
            totals = totals == null ? List.of() : List.copyOf(totals);
            extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
        }

        @JsonAnySetter
        public void putExtension(String name, JsonNode value) {
            extensions.put(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> extensionValues() {
            return extensions;
        }
    }

    public record FulfillmentTotal(
            String type,
            @JsonProperty("display_text") String displayText,
            Long amount
    ) {
    }
}
