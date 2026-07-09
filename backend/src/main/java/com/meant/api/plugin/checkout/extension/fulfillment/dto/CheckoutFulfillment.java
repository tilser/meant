package com.meant.api.plugin.checkout.extension.fulfillment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CheckoutFulfillment(
        List<Map<String, Object>> methods
) {

    public CheckoutFulfillment {
        methods = immutableMapList(methods);
    }

    public boolean empty() {
        return methods.isEmpty();
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
