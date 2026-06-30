package com.meant.api.plugin.payment.common.support;

import com.meant.api.plugin.payment.common.exception.PaymentHandlerException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class PaymentHandlerJson {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private PaymentHandlerJson() {
    }

    public static Map<String, Object> map(ObjectMapper objectMapper, Object value, String context) {
        if (value == null) {
            throw new PaymentHandlerException(context + " must not be null");
        }
        if (value instanceof Map<?, ?> map) {
            return stringKeyMap(map);
        }
        try {
            if (value instanceof String string) {
                return objectMapper.readValue(string, MAP_TYPE);
            }
            Map<String, Object> converted = objectMapper.convertValue(value, MAP_TYPE);
            if (converted == null) {
                throw new PaymentHandlerException(context + " must not be null");
            }
            return converted;
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new PaymentHandlerException(context + " could not be parsed", exception);
        }
    }

    public static <T> T convert(ObjectMapper objectMapper, Object value, Class<T> type, String context) {
        try {
            if (value instanceof String string) {
                return objectMapper.readValue(string, type);
            }
            return objectMapper.convertValue(value, type);
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new PaymentHandlerException(context + " could not be parsed", exception);
        }
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, mapValue) -> {
            if (key != null) {
                values.put(key.toString(), mapValue);
            }
        });
        return values;
    }
}
