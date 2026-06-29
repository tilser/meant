package com.meant.api.plugin.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Map;

public record UcpMoney(
        Long amount,
        String currency
) {

    public static UcpMoney value(Object value, String fallbackCurrency) {
        if (value == null) {
            return null;
        }
        if (value instanceof UcpMoney money) {
            return money.amount() == null
                    ? null
                    : new UcpMoney(money.amount(), firstPresent(money.currency(), fallbackCurrency));
        }
        UcpMoney recordMoney = recordMoney(value, fallbackCurrency);
        if (recordMoney != null) {
            return recordMoney;
        }
        if (value instanceof Map<?, ?> map) {
            String currency = firstPresent(firstStringValue(map, "currency", "currencyCode"), fallbackCurrency);
            Object explicitMinorAmount = UcpDecimal.firstMapValue(map,
                    "minorAmount",
                    "minor_amount",
                    "amountMinor",
                    "amount_minor",
                    "amountInMinorUnits",
                    "amount_in_minor_units",
                    "amountCents",
                    "amount_cents",
                    "cents");
            Long explicitMinor = wholeNumberAmount(explicitMinorAmount);
            if (explicitMinor != null) {
                return new UcpMoney(explicitMinor, currency);
            }
            Object amount = UcpDecimal.firstMapValue(map, "amount", "value", "price", "min");
            Long minorAmount = hasMinorUnitHint(map)
                    ? wholeNumberAmount(amount)
                    : minorAmount(amount, currency);
            return minorAmount == null ? null : new UcpMoney(minorAmount, currency);
        }
        Long minorAmount = minorAmount(value, fallbackCurrency);
        return minorAmount == null ? null : new UcpMoney(minorAmount, fallbackCurrency);
    }

    public static Long minorAmount(Object value, String currency) {
        if (value == null) {
            return null;
        }
        return UcpDecimal.decimalAmountToMinor(value.toString(), currency);
    }

    public static Long wholeNumberAmount(Object value) {
        if (value == null) {
            return null;
        }
        String amount = value.toString().trim();
        if (amount.isBlank()) {
            return null;
        }
        String wholeNumber = amount.replace(",", "").replaceAll("\\s+", "");
        if (!wholeNumber.matches("-?\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(wholeNumber);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static UcpMoney recordMoney(Object value, String fallbackCurrency) {
        if (!value.getClass().isRecord()) {
            return null;
        }
        Object amount = recordComponentValue(value, "amount");
        if (amount == null) {
            return null;
        }
        Long minorAmount = wholeNumberAmount(amount);
        if (minorAmount == null) {
            return null;
        }
        String currency = firstPresent(UcpDecimal.scalarString(recordComponentValue(value, "currency")), fallbackCurrency);
        return new UcpMoney(minorAmount, currency);
    }

    private static Object recordComponentValue(Object value, String componentName) {
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            if (!componentName.equals(component.getName())) {
                continue;
            }
            try {
                return component.getAccessor().invoke(value);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasMinorUnitHint(Map<?, ?> map) {
        String unit = firstStringValue(map,
                "unit",
                "units",
                "amountUnit",
                "amount_unit",
                "scale",
                "format");
        if (unit == null) {
            return false;
        }
        String normalized = unit.trim().toLowerCase();
        return normalized.contains("minor")
                || normalized.equals("cent")
                || normalized.equals("cents")
                || normalized.equals("centavo")
                || normalized.equals("centavos");
    }

    private static String firstStringValue(Map<?, ?> map, String... keys) {
        Object value = UcpDecimal.firstMapValue(map, keys);
        return UcpDecimal.scalarString(value);
    }

    private static String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
