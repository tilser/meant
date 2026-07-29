package com.meant.api.common.util;

import java.math.BigDecimal;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts one explicit, concrete BCP 47 preference from a raw HTTP Accept-Language header.
 *
 * <p>The parser never consults a servlet or JVM locale fallback. Invalid language ranges,
 * wildcards, and zero-quality ranges are ignored rather than converted into an inferred value.
 */
public final class AcceptLanguageParser {

    public static final int MAXIMUM_LANGUAGE_TAG_LENGTH = 255;
    private static final int MAXIMUM_HEADER_LENGTH = 2048;
    private static final Pattern QUALITY_PARAMETER = Pattern.compile(
            "(?i)^q\\s*=\\s*(0(?:\\.\\d{0,3})?|1(?:\\.0{0,3})?)$"
    );

    private AcceptLanguageParser() {
    }

    public static String preferredLanguage(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()
                || rawHeader.length() > MAXIMUM_HEADER_LENGTH
                || rawHeader.indexOf('\r') >= 0
                || rawHeader.indexOf('\n') >= 0) {
            return null;
        }

        Candidate preferred = null;
        String[] ranges = rawHeader.split(",", -1);
        for (String range : ranges) {
            Candidate candidate = candidate(range);
            if (candidate != null
                    && (preferred == null || candidate.quality().compareTo(preferred.quality()) > 0)) {
                preferred = candidate;
            }
        }
        return preferred == null ? null : preferred.language();
    }

    public static String canonicalLanguageTag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.length() > MAXIMUM_LANGUAGE_TAG_LENGTH || candidate.indexOf('*') >= 0) {
            return null;
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(candidate).build();
            String canonical = locale.toLanguageTag();
            if (locale.getLanguage().isBlank()
                    || "und".equalsIgnoreCase(canonical)
                    || canonical.length() > MAXIMUM_LANGUAGE_TAG_LENGTH) {
                return null;
            }
            return canonical;
        } catch (IllformedLocaleException exception) {
            return null;
        }
    }

    private static Candidate candidate(String range) {
        String[] parts = range.split(";", -1);
        if (parts.length > 2) {
            return null;
        }
        String language = canonicalLanguageTag(parts[0]);
        if (language == null) {
            return null;
        }
        BigDecimal quality = BigDecimal.ONE;
        if (parts.length == 2) {
            Matcher matcher = QUALITY_PARAMETER.matcher(parts[1].trim());
            if (!matcher.matches()) {
                return null;
            }
            quality = new BigDecimal(matcher.group(1));
        }
        return quality.signum() == 0 ? null : new Candidate(language, quality);
    }

    private record Candidate(String language, BigDecimal quality) {
    }
}
