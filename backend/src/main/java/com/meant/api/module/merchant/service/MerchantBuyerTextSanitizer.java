package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes provider transport coordinates from text that can cross a buyer-facing boundary.
 *
 * <p>Checkout and product URLs remain data fields owned by their respective responses. This
 * sanitizer only rewrites free-form text supplied by a merchant/provider.
 */
public final class MerchantBuyerTextSanitizer {

    private static final String NEUTRAL_MERCHANT = "the merchant";
    private static final List<String> PROTOCOL_PATHS = List.of(
            "/.well-known/ucp.json",
            "/.well-known/ucp",
            "/api/ucp/mcp",
            "/api/mcp",
            "/mcp"
    );
    private static final String GENERIC_TECHNICAL_HOST = "(?:"
            + "(?:[A-Z0-9-]+\\.)*?[A-Z0-9-]+\\.myshopify\\.com"
            + "|mcp(?:\\.[A-Z0-9-]+)+"
            + "|(?:[A-Z0-9-]+\\.)+mcp(?:\\.[A-Z0-9-]+)+"
            + ")";
    private static final Pattern GENERIC_TECHNICAL_HOST_PATTERN = Pattern.compile(
            GENERIC_TECHNICAL_HOST,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern GENERIC_TECHNICAL_URL_PATTERN = Pattern.compile(
            "(?<![A-Z0-9.-])https?://"
                    + GENERIC_TECHNICAL_HOST
                    + "(?::\\d{1,5})?"
                    + "(?:/[^\\s<>{}\\[\\]\"']*)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern GENERIC_TECHNICAL_HOST_REFERENCE_PATTERN = Pattern.compile(
            "(?<![A-Z0-9.-])"
                    + GENERIC_TECHNICAL_HOST
                    + "(?::\\d{1,5})?"
                    + "(?![A-Z0-9-]|\\.[A-Z0-9-])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final ObjectMapper JSON = new ObjectMapper();

    private MerchantBuyerTextSanitizer() {
    }

    public static String sanitize(String value) {
        return sanitize(value, null, null, List.of());
    }

    public static String sanitizeJson(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            JsonNode parsed = JSON.readTree(value);
            if (parsed == null) {
                return sanitize(value);
            }
            return JSON.writeValueAsString(sanitizeJsonNode(parsed));
        } catch (JacksonException exception) {
            return sanitize(value);
        }
    }

    /** Returns a normalized verified storefront origin suitable for buyer display. */
    public static String buyerSafeMerchantOrigin(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || "http".equalsIgnoreCase(uri.getScheme()))
                    || !"/".equals(normalizedOriginPath(uri.getPath()))) {
                return null;
            }
            String normalized = uri.getHost().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("mcp.") || normalized.contains(".mcp.")) {
                return null;
            }
            return uri.getPort() < 0 ? normalized : normalized + ":" + uri.getPort();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static String sanitize(String value, MerchantCartProvider provider) {
        if (provider == null) {
            return sanitize(value);
        }
        List<String> endpoints = new ArrayList<>();
        endpoints.add(provider.advertisedMcpEndpoint());
        endpoints.add(provider.profileMcpEndpoint());
        provider.integrations().forEach(integration -> endpoints.add(integration.endpoint()));
        return sanitize(
                value,
                provider.merchantDomain(),
                provider.routingDomain(),
                endpoints
        );
    }

    public static String sanitize(
            String value,
            String merchantDomain,
            String routingDomain,
            String endpoint
    ) {
        return sanitize(
                value,
                merchantDomain,
                routingDomain,
                endpoint == null ? List.of() : List.of(endpoint)
        );
    }

    /**
     * Replaces one known transport endpoint with its verified merchant origin without treating
     * other verified merchant origins in the same payload as routing coordinates.
     */
    public static String sanitizeTechnicalEndpoint(
            String value,
            String merchantDomain,
            String endpoint
    ) {
        if (value == null || value.isEmpty() || !hasText(endpoint)) {
            return value;
        }
        String presentationOrigin = presentationOrigin(merchantDomain);
        String presentationHost = presentationHost(merchantDomain);
        String trimmedEndpoint = endpoint.trim();
        String endpointHost = host(trimmedEndpoint);
        String withoutTrailingSlash = trimmedEndpoint.replaceFirst("/+$", "");
        String sanitized = replaceEndpointUrl(value, withoutTrailingSlash, presentationOrigin);
        sanitized = replaceLiteral(sanitized, trimmedEndpoint, presentationOrigin);
        if (!withoutTrailingSlash.isEmpty()) {
            sanitized = replaceLiteral(sanitized, withoutTrailingSlash, presentationOrigin);
        }
        if (hasText(endpointHost) && !endpointHost.equalsIgnoreCase(presentationHost)) {
            sanitized = replaceProtocolUrls(sanitized, endpointHost, presentationOrigin);
            sanitized = replaceHost(sanitized, endpointHost, presentationHost);
        }
        return sanitized;
    }

    private static String replaceEndpointUrl(String value, String endpoint, String replacement) {
        if (!hasText(endpoint)) {
            return value;
        }
        Pattern pattern = Pattern.compile(
                Pattern.quote(endpoint)
                        + "(?:[/\\\\?#][^\\s<>{}\\[\\]\"']*)?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return replaceMatchesPreservingTrailingPunctuation(value, pattern, replacement);
    }

    private static String sanitize(
            String value,
            String merchantDomain,
            String routingDomain,
            List<String> endpoints
    ) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String presentationOrigin = presentationOrigin(merchantDomain);
        String presentationHost = presentationHost(merchantDomain);
        Set<String> technicalEndpoints = new LinkedHashSet<>();
        Set<String> technicalHosts = new LinkedHashSet<>();
        addHost(technicalHosts, routingDomain);
        for (String endpoint : endpoints) {
            if (!hasText(endpoint)) {
                continue;
            }
            String trimmed = endpoint.trim();
            technicalEndpoints.add(trimmed);
            String withoutTrailingSlash = trimmed.replaceFirst("/+$", "");
            if (!withoutTrailingSlash.isEmpty()) {
                technicalEndpoints.add(withoutTrailingSlash);
            }
            addHost(technicalHosts, host(trimmed));
        }

        String sanitized = replaceGenericTechnicalUrls(value, presentationOrigin);
        sanitized = replaceAnyProtocolUrls(sanitized, presentationOrigin);
        sanitized = replaceStandaloneProtocolPaths(sanitized, presentationOrigin);
        sanitized = replaceGenericTechnicalHosts(sanitized, presentationOrigin);
        for (String technicalHost : longestFirst(technicalHosts)) {
            sanitized = replaceProtocolUrls(
                    sanitized,
                    technicalHost,
                    presentationOrigin
            );
        }
        for (String endpoint : longestFirst(technicalEndpoints)) {
            sanitized = replaceLiteral(sanitized, endpoint, presentationOrigin);
        }
        for (String technicalHost : longestFirst(technicalHosts)) {
            if (!technicalHost.equalsIgnoreCase(presentationHost)) {
                sanitized = replaceHost(sanitized, technicalHost, presentationHost);
            }
        }
        return sanitized;
    }

    private static JsonNode sanitizeJsonNode(JsonNode value) {
        if (value.isTextual()) {
            return JsonNodeFactory.instance.textNode(sanitize(value.asText()));
        }
        if (value.isObject()) {
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            value.properties().forEach(entry ->
                    sanitized.set(entry.getKey(), sanitizeJsonNode(entry.getValue())));
            return sanitized;
        }
        if (value.isArray()) {
            ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> sanitized.add(sanitizeJsonNode(item)));
            return sanitized;
        }
        return value.deepCopy();
    }

    private static String replaceGenericTechnicalUrls(String value, String replacement) {
        return replaceMatchesPreservingTrailingPunctuation(
                value,
                GENERIC_TECHNICAL_URL_PATTERN,
                replacement
        );
    }

    private static String replaceGenericTechnicalHosts(String value, String replacement) {
        return GENERIC_TECHNICAL_HOST_REFERENCE_PATTERN.matcher(value)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String replaceAnyProtocolUrls(String value, String replacement) {
        String sanitized = value;
        for (String path : PROTOCOL_PATHS) {
            Pattern pattern = Pattern.compile(
                    "(?<![A-Z0-9.-])https?://"
                            + "(?:\\[[0-9A-F:]+]|[A-Z0-9.-]+)"
                            + "(?::\\d{1,5})?"
                            + Pattern.quote(path)
                            + "(?=$|[/\\\\?#\\s<>{}\\[\\]\"'.,;!:)}])"
                            + "(?:[/\\\\?#][^\\s<>{}\\[\\]\"']*)?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            sanitized = replaceMatchesPreservingTrailingPunctuation(
                    sanitized,
                    pattern,
                    replacement
            );
        }
        return sanitized;
    }

    private static String replaceStandaloneProtocolPaths(String value, String replacement) {
        String sanitized = value;
        for (String path : PROTOCOL_PATHS) {
            Pattern pattern = Pattern.compile(
                    "(?<![A-Z0-9_./-])"
                            + Pattern.quote(path)
                            + "(?=$|[/\\\\?#\\s<>{}\\[\\]\"'.,;!:)}])"
                            + "(?:[/\\\\?#][^\\s<>{}\\[\\]\"']*)?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            sanitized = replaceMatchesPreservingTrailingPunctuation(
                    sanitized,
                    pattern,
                    replacement
            );
        }
        return sanitized;
    }

    private static List<String> longestFirst(Set<String> values) {
        return values.stream()
                .filter(MerchantBuyerTextSanitizer::hasText)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static String replaceLiteral(String value, String needle, String replacement) {
        return Pattern.compile(Pattern.quote(needle), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(value)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String replaceHost(String value, String host, String replacement) {
        Pattern pattern = Pattern.compile(
                "(?<![A-Z0-9.-])"
                        + Pattern.quote(host)
                        + "(?::\\d{1,5})?"
                        + "(?![A-Z0-9-]|\\.[A-Z0-9-])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return pattern.matcher(value).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static String replaceProtocolUrls(String value, String host, String replacement) {
        String sanitized = value;
        for (String path : PROTOCOL_PATHS) {
            Pattern pattern = Pattern.compile(
                    "(?<![A-Z0-9.-])(?:https?://)?"
                            + Pattern.quote(host)
                            + "(?::\\d{1,5})?"
                            + Pattern.quote(path)
                            + "/?(?![/A-Z0-9_-])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            sanitized = pattern.matcher(sanitized).replaceAll(Matcher.quoteReplacement(replacement));
        }
        return sanitized;
    }

    private static void addHost(Set<String> hosts, String value) {
        if (!hasText(value)) {
            return;
        }
        String host = host(value);
        if (hasText(host)) {
            hosts.add(host.toLowerCase(Locale.ROOT));
        }
    }

    private static String presentationOrigin(String merchantDomain) {
        String origin = buyerSafeMerchantOrigin(merchantDomain);
        return origin == null ? NEUTRAL_MERCHANT : origin;
    }

    private static String presentationHost(String merchantDomain) {
        String presentationOrigin = presentationOrigin(merchantDomain);
        if (NEUTRAL_MERCHANT.equals(presentationOrigin)) {
            return NEUTRAL_MERCHANT;
        }
        String host = host(presentationOrigin);
        return hasText(host) ? host : presentationOrigin;
    }

    private static String replaceMatchesPreservingTrailingPunctuation(
            String value,
            Pattern pattern,
            String replacement
    ) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder sanitized = new StringBuilder(value.length());
        while (matcher.find()) {
            String match = matcher.group();
            int contentEnd = match.length();
            while (contentEnd > 0 && trailingPunctuation(match.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            String trailing = match.substring(contentEnd);
            matcher.appendReplacement(
                    sanitized,
                    Matcher.quoteReplacement(replacement + trailing)
            );
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private static boolean trailingPunctuation(char value) {
        return value == '.' || value == ',' || value == ';' || value == '!'
                || value == '?' || value == ':' || value == ')'
                || value == ']' || value == '}';
    }

    private static String host(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            URI uri = URI.create(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            return uri.getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizedOriginPath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String trimmed = value.trim();
        return trimmed.replaceFirst("/+$", "").isEmpty() ? "/" : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
