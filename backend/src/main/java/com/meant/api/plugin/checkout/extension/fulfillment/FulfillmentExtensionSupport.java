package com.meant.api.plugin.checkout.extension.fulfillment;

import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FulfillmentExtensionSupport {

    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.fulfillment");

    private FulfillmentExtensionSupport() {
    }

    public static boolean active(NegotiatedCapabilities activeCapabilities) {
        return activeCapabilities != null && activeCapabilities.supports(ID);
    }

    public static CheckoutFulfillment fulfillment(
            Map<String, Object> shippingAddress,
            Map<String, Object> fulfillment
    ) {
        Map<String, Object> values = fulfillment == null ? Map.of() : fulfillment;
        Map<String, Object> resolvedShippingAddress = shippingAddress == null || shippingAddress.isEmpty()
                ? mapValue(values.get("shipping_address"), values.get("shippingAddress"), values.get("address"))
                : new LinkedHashMap<>(shippingAddress);
        CheckoutFulfillment result = new CheckoutFulfillment(
                resolvedShippingAddress,
                listOfMaps(values.get("methods")),
                listOfMaps(firstValue(values, "available_methods", "availableMethods"))
        );
        return result.empty() ? null : result;
    }

    private static Object firstValue(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> mapValue(Object... values) {
        for (Object value : values) {
            if (value instanceof Map<?, ?> source) {
                Map<String, Object> map = new LinkedHashMap<>();
                source.forEach((key, mapValue) -> {
                    if (key != null) {
                        map.put(key.toString(), mapValue);
                    }
                });
                return map;
            }
        }
        return Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(FulfillmentExtensionSupport::mapValue)
                .filter(map -> !map.isEmpty())
                .toList();
    }
}
