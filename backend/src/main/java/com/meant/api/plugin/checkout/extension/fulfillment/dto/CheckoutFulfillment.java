package com.meant.api.plugin.checkout.extension.fulfillment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CheckoutFulfillment(
        @JsonProperty("shipping_address")
        Map<String, Object> shippingAddress,
        List<Map<String, Object>> methods,
        @JsonProperty("available_methods")
        List<Map<String, Object>> availableMethods
) {

    public CheckoutFulfillment {
        shippingAddress = immutableMap(shippingAddress);
        methods = immutableMapList(methods);
        availableMethods = immutableMapList(availableMethods);
    }

    public boolean empty() {
        return shippingAddress.isEmpty() && methods.isEmpty() && availableMethods.isEmpty();
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        return values == null || values.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    private static List<Map<String, Object>> immutableMapList(List<Map<String, Object>> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isEmpty())
                        .map(CheckoutFulfillment::immutableMap)
                        .toList();
    }
}
