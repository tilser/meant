package com.meant.api.plugin.catalog.common.support;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Conservative value normalization shared by the typed identity evidence chain. */
public final class ProductIdentityNormalizationSupport {

    private ProductIdentityNormalizationSupport() {
    }

    public static String token(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static Optional<String> universalTradeItemNumber(String value) {
        String input = value == null ? "" : value.trim();
        if (!input.matches("[0-9 -]+")) {
            return Optional.empty();
        }
        String digits = input.replace(" ", "").replace("-", "");
        if (digits.length() < 8 || digits.length() > 14) {
            return Optional.empty();
        }
        return Optional.of("0".repeat(14 - digits.length()) + digits);
    }

    public static Optional<String> typedStandardIdentifier(ExternalIdentifier identifier) {
        String namespace = token(identifier.namespace());
        String value = token(identifier.value());
        return namespace.isEmpty() || value.isEmpty()
                ? Optional.empty()
                : Optional.of(namespace + ":" + value);
    }

    public static Optional<String> canonicalUrl(String value) {
        try {
            URI raw = new URI(value.trim());
            if (raw.getHost() == null || !("http".equalsIgnoreCase(raw.getScheme())
                    || "https".equalsIgnoreCase(raw.getScheme()))) {
                return Optional.empty();
            }
            String path = raw.normalize().getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            URI normalized = new URI(
                    "https",
                    null,
                    raw.getHost().toLowerCase(Locale.ROOT),
                    normalizedPort(raw),
                    path,
                    normalizedQuery(raw.getRawQuery()),
                    null
            );
            return Optional.of(normalized.toASCIIString());
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return Optional.empty();
        }
    }

    private static int normalizedPort(URI uri) {
        return uri.getPort() == 80 || uri.getPort() == 443 ? -1 : uri.getPort();
    }

    private static String normalizedQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = Arrays.stream(query.split("&"))
                .filter(Predicate.not(String::isBlank))
                .filter(parameter -> {
                    String name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
                    return !name.startsWith("utm_") && !Set.of("gclid", "fbclid").contains(name);
                })
                .sorted()
                .collect(Collectors.joining("&"));
        return normalized.isBlank() ? null : normalized;
    }
}
