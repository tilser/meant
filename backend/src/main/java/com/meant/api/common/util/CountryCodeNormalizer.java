package com.meant.api.common.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

/** Normalizes supported country aliases to ISO 3166-1 alpha-2 codes. */
@UtilityClass
public class CountryCodeNormalizer {
    private static final Map<String, String> ALIASES = Map.of("UK", "GB");
    private static final Set<String> ISO_ALPHA_2 = Set.of(Locale.getISOCountries());

    public String normalizeAlpha2(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 2
                || !isAsciiUppercase(normalized.charAt(0))
                || !isAsciiUppercase(normalized.charAt(1))) {
            return null;
        }
        String canonical = ALIASES.getOrDefault(normalized, normalized);
        return ISO_ALPHA_2.contains(canonical) ? canonical : null;
    }

    private boolean isAsciiUppercase(char value) {
        return value >= 'A' && value <= 'Z';
    }
}
