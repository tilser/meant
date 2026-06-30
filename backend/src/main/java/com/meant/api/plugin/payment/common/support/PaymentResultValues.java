package com.meant.api.plugin.payment.common.support;

import com.meant.api.plugin.payment.common.exception.PaymentHandlerException;
import com.meant.api.plugin.support.UcpMoney;
import java.util.Map;

public final class PaymentResultValues {

    private PaymentResultValues() {
    }

    public static String text(Map<String, Object> source, String fieldName, String... keys) {
        Object value = firstValue(source, keys);
        if (value == null) {
            throw new PaymentHandlerException(fieldName + " is required");
        }
        return PaymentBindingValidator.requireText(value.toString(), fieldName);
    }

    public static String optionalText(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }

    public static Long amountMinor(Map<String, Object> source, String fieldName, String... keys) {
        Object value = firstValue(source, keys);
        Long amount = amountValue(value);
        if (amount == null) {
            throw new PaymentHandlerException(fieldName + " must be a minor-unit integer");
        }
        return amount;
    }

    public static Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static Long amountValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object explicitMinor = firstMapValue(map, "amount", "amount_minor", "amountMinor", "minor_amount");
            return UcpMoney.wholeNumberAmount(explicitMinor);
        }
        return UcpMoney.wholeNumberAmount(value);
    }

    private static Object firstMapValue(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toString().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
