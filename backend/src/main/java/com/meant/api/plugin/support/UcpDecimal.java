package com.meant.api.plugin.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

public final class UcpDecimal {

    private UcpDecimal() {
    }

    public static Long decimalAmountToMinor(String amount, String currency) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String cleaned = amount.trim()
                .replaceAll("[^0-9,.\\-]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        int exponent = currencyExponent(currency);
        cleaned = normalizedDecimal(cleaned, exponent);
        if (cleaned == null) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(cleaned);
            return decimal
                    .movePointRight(exponent)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static Double ratingValue(Object value) {
        Object ratingValue = value;
        if (value instanceof Map<?, ?> map) {
            ratingValue = firstMapValue(map, "ratingValue", "rating_value", "value", "average", "score", "rating");
        }
        Double rating = decimalValue(ratingValue);
        if (rating == null) {
            return null;
        }
        if (rating > 5.0d && rating <= 10.0d) {
            return rating / 2.0d;
        }
        if (rating > 10.0d && rating <= 100.0d) {
            return rating / 20.0d;
        }
        return rating;
    }

    public static Integer reviewCountValue(Object value) {
        Object countValue = value;
        if (value instanceof Map<?, ?> map) {
            countValue = firstMapValue(map, "reviewCount", "review_count", "reviewsCount", "reviews_count",
                    "ratingCount", "rating_count", "count");
        }
        Double count = decimalValue(countValue);
        return count == null ? null : Math.max(0, count.intValue());
    }

    public static Double decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String scalar = scalarString(value);
        if (scalar == null) {
            return null;
        }
        String cleaned = scalar.trim().replaceAll("[^0-9,.\\-]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        cleaned = normalizedDecimal(cleaned, 2);
        if (cleaned == null) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static int currencyExponent(String currency) {
        return switch (normalizedCurrency(currency) == null ? "" : normalizedCurrency(currency)) {
            case "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND",
                    "VUV", "XAF", "XOF", "XPF" -> 0;
            case "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3;
            default -> 2;
        };
    }

    public static String normalizedCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    static String normalizedDecimal(String value, int exponent) {
        if (value.contains(".") && value.contains(",")) {
            int lastDot = value.lastIndexOf('.');
            int lastComma = value.lastIndexOf(',');
            return lastComma > lastDot
                    ? value.replace(".", "").replace(',', '.')
                    : value.replace(",", "");
        }
        if (value.contains(",")) {
            return normalizeSingleSeparatorDecimal(value, ',', exponent);
        }
        if (value.contains(".")) {
            return normalizeSingleSeparatorDecimal(value, '.', exponent);
        }
        return value;
    }

    private static String normalizeSingleSeparatorDecimal(String value, char separator, int exponent) {
        int firstSeparator = value.indexOf(separator);
        int lastSeparator = value.lastIndexOf(separator);
        if (firstSeparator != lastSeparator) {
            return hasGroupedThousands(value, separator)
                    ? value.replace(String.valueOf(separator), "")
                    : null;
        }
        int signedOffset = value.startsWith("-") ? 1 : 0;
        int integralDigits = lastSeparator - signedOffset;
        int fractionalDigits = value.length() - lastSeparator - 1;
        if (separator == ',' && fractionalDigits == 3 && integralDigits >= 1 && integralDigits <= 3) {
            return exponent == 3
                    ? value.replace(',', '.')
                    : value.replace(String.valueOf(separator), "");
        }
        return separator == ',' ? value.replace(',', '.') : value;
    }

    private static boolean hasGroupedThousands(String value, char separator) {
        int start = value.startsWith("-") ? 1 : 0;
        int firstSeparator = value.indexOf(separator, start);
        if (firstSeparator <= start || firstSeparator - start > 3) {
            return false;
        }
        int groupStart = firstSeparator + 1;
        while (groupStart < value.length()) {
            int nextSeparator = value.indexOf(separator, groupStart);
            int groupEnd = nextSeparator == -1 ? value.length() : nextSeparator;
            if (groupEnd - groupStart != 3) {
                return false;
            }
            groupStart = groupEnd + 1;
        }
        return groupStart == value.length() + 1;
    }

    static Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    static String scalarString(Object value) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
