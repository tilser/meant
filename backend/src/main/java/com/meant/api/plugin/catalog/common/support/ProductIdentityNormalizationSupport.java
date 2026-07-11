package com.meant.api.plugin.catalog.common.support;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
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

    public static Optional<String> universalTradeItemNumber(
            ProductIdentityEvidenceKind kind,
            String value
    ) {
        if (kind != ProductIdentityEvidenceKind.GTIN
                && kind != ProductIdentityEvidenceKind.UPC
                && kind != ProductIdentityEvidenceKind.EAN) {
            return Optional.empty();
        }
        String input = value == null ? "" : value.trim();
        if (!input.matches("[0-9 -]+")) {
            return Optional.empty();
        }
        String digits = input.replace(" ", "").replace("-", "");
        if (!supportedLength(kind, digits.length())
                || digits.chars().distinct().count() == 1
                || !validGs1CheckDigit(digits)) {
            return Optional.empty();
        }
        return Optional.of("0".repeat(14 - digits.length()) + digits);
    }

    private static boolean supportedLength(ProductIdentityEvidenceKind kind, int length) {
        return switch (kind) {
            case GTIN -> Set.of(8, 12, 13, 14).contains(length);
            case UPC -> length == 12;
            case EAN -> length == 8 || length == 13;
            default -> false;
        };
    }

    private static boolean validGs1CheckDigit(String digits) {
        int sum = 0;
        boolean triple = true;
        for (int index = digits.length() - 2; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            sum += triple ? digit * 3 : digit;
            triple = !triple;
        }
        return (10 - sum % 10) % 10 == digits.charAt(digits.length() - 1) - '0';
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
