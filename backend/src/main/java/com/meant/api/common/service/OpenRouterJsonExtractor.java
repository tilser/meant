package com.meant.api.common.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class OpenRouterJsonExtractor {

    private static final Pattern COMMENT_PATTERN = Pattern.compile("\\s+#.*$");
    private static final Pattern TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*$");

    private OpenRouterJsonExtractor() {
    }

    public static String objectCandidate(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        return firstObject(response).orElse(response.trim());
    }

    public static Optional<String> firstObject(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }

        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return Optional.of(trimmed.substring(start, index + 1));
                }
            }
        }
        return Optional.empty();
    }

    public static Map<String, String> looseKeyValues(String response, Collection<String> keys) {
        Map<String, String> values = new LinkedHashMap<>();
        if (response == null || response.isBlank() || keys == null || keys.isEmpty()) {
            return values;
        }

        Map<String, String> canonicalKeys = new LinkedHashMap<>();
        keys.forEach(key -> canonicalKeys.put(key.toLowerCase(Locale.ROOT), key));

        response.lines()
                .map(OpenRouterJsonExtractor::stripLooseLinePrefix)
                .forEach(line -> putLooseValue(values, canonicalKeys, line));
        return values;
    }

    public static String cleanLooseValue(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = TRAILING_COMMA_PATTERN.matcher(
                        COMMENT_PATTERN.matcher(value).replaceAll(""))
                .replaceAll("")
                .trim();
        while (isWrapped(cleaned, '"')
                || isWrapped(cleaned, '\'')
                || isWrapped(cleaned, '`')) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static void putLooseValue(
            Map<String, String> values,
            Map<String, String> canonicalKeys,
            String line
    ) {
        if (line.isBlank()) {
            return;
        }
        int separatorIndex = separatorIndex(line);
        if (separatorIndex <= 0) {
            return;
        }

        String rawKey = line.substring(0, separatorIndex)
                .replaceAll("^[\"'`]+|[\"'`]+$", "")
                .trim()
                .toLowerCase(Locale.ROOT);
        String canonicalKey = canonicalKeys.get(rawKey);
        if (canonicalKey == null || values.containsKey(canonicalKey)) {
            return;
        }

        String value = cleanLooseValue(line.substring(separatorIndex + 1));
        if (value != null && !value.isBlank()) {
            values.put(canonicalKey, value);
        }
    }

    private static String stripLooseLinePrefix(String line) {
        return line
                .replaceFirst("^\\s*(?:[-*]|\\u2022)\\s*", "")
                .trim();
    }

    private static int separatorIndex(String line) {
        int colon = line.indexOf(':');
        int equals = line.indexOf('=');
        if (colon < 0) {
            return equals;
        }
        if (equals < 0) {
            return colon;
        }
        return Math.min(colon, equals);
    }

    private static boolean isWrapped(String value, char wrapper) {
        return value.length() >= 2
                && value.charAt(0) == wrapper
                && value.charAt(value.length() - 1) == wrapper;
    }
}
