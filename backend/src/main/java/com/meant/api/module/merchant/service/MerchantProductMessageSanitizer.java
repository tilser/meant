package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Sanitizes provider product messages before they cross a buyer-facing boundary. */
public final class MerchantProductMessageSanitizer {

    private static final Set<String> MESSAGE_TYPES =
            Set.of("info", "notice", "warning", "error", "success", "disclosure");
    private static final Set<String> SEVERITIES = Set.of(
            "info",
            "warning",
            "error",
            "success",
            "recoverable",
            "unrecoverable",
            "requires_buyer_input",
            "requires_buyer_review"
    );
    private static final Set<String> PRESENTATIONS = Set.of("inline", "disclosure");
    private static final Set<String> CONTENT_TYPES =
            Set.of("plain", "markdown", "text/plain", "text/markdown");
    private static final Set<String> MEDIA_TYPES =
            Set.of("image", "video", "model", "model_3d", "document", "external_video", "other");
    private static final List<String> PROTOCOL_PATHS = List.of(
            "/.well-known/ucp.json",
            "/.well-known/ucp",
            "/api/ucp/mcp",
            "/api/mcp",
            "/mcp"
    );
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern SAFE_PATH = Pattern.compile("/[A-Za-z0-9_./~:-]{0,255}");

    private MerchantProductMessageSanitizer() {
    }

    public static TransportContext context(ProductDetailsResult result) {
        if (result == null) {
            return new TransportContext(null, null, List.of());
        }
        List<String> endpoints = new ArrayList<>(result.technicalEndpointAliases());
        endpoints.add(result.endpoint());
        return new TransportContext(result.merchantDomain(), result.endpoint(), endpoints);
    }

    public static TransportContext context(
            String merchantDomain,
            String routingDomain,
            String... endpoints
    ) {
        List<String> values = new ArrayList<>();
        if (endpoints != null) {
            for (String endpoint : endpoints) {
                if (hasText(endpoint)) {
                    values.add(endpoint.trim());
                }
            }
        }
        return new TransportContext(merchantDomain, routingDomain, values);
    }

    public static SanitizedMessage sanitize(
            ProductDetailsResponse.Message message,
            ProductDetailsResult result
    ) {
        if (message == null) {
            return null;
        }
        return sanitize(
                message.type(),
                message.code(),
                message.path(),
                message.contentType(),
                message.content(),
                message.severity(),
                message.presentation(),
                message.imageUrl(),
                message.url(),
                context(result)
        );
    }

    public static SanitizedMessage sanitize(
            String type,
            String code,
            String path,
            String contentType,
            String content,
            String severity,
            String presentation,
            String imageUrl,
            String url,
            TransportContext context
    ) {
        TransportContext effectiveContext =
                context == null ? new TransportContext(null, null, List.of()) : context;
        return new SanitizedMessage(
                allowlisted(type, MESSAGE_TYPES, "notice"),
                safeCode(code, effectiveContext),
                safePath(path, effectiveContext),
                allowlisted(contentType, CONTENT_TYPES, "text/plain"),
                sanitizeBuyerText(content, effectiveContext),
                allowlisted(severity, SEVERITIES, null),
                allowlisted(presentation, PRESENTATIONS, "inline"),
                buyerSafeUrl(imageUrl, effectiveContext),
                buyerSafeUrl(url, effectiveContext)
        );
    }

    public static String sanitizeBuyerText(String value, TransportContext context) {
        TransportContext effectiveContext =
                context == null ? new TransportContext(null, null, List.of()) : context;
        String sanitized = value;
        for (String endpoint : effectiveContext.endpoints()) {
            sanitized = MerchantBuyerTextSanitizer.sanitizeTechnicalEndpoint(
                    sanitized,
                    effectiveContext.merchantDomain(),
                    endpoint
            );
        }
        return MerchantBuyerTextSanitizer.sanitize(
                sanitized,
                effectiveContext.merchantDomain(),
                effectiveContext.routingDomain(),
                (String) null
        );
    }

    public static String buyerSafeMediaType(String value, TransportContext context) {
        String sanitized = trimToNull(sanitizeBuyerText(value, context));
        if (sanitized == null) {
            return "other";
        }
        String normalized = sanitized.toLowerCase(Locale.ROOT);
        return MEDIA_TYPES.contains(normalized) ? normalized : "other";
    }

    private static String safeCode(String value, TransportContext context) {
        String trimmed = trimToNull(value);
        return trimmed != null
                && SAFE_CODE.matcher(trimmed).matches()
                && trimmed.equals(sanitizeBuyerText(trimmed, context))
                        ? trimmed
                        : null;
    }

    private static String safePath(String value, TransportContext context) {
        String trimmed = trimToNull(value);
        return trimmed != null
                && SAFE_PATH.matcher(trimmed).matches()
                && !trimmed.contains("..")
                && !protocolPath(trimmed)
                && trimmed.equals(sanitizeBuyerText(trimmed, context))
                        ? trimmed
                        : null;
    }

    public static String buyerSafeUrl(String value, TransportContext context) {
        TransportContext effectiveContext =
                context == null ? new TransportContext(null, null, List.of()) : context;
        URI candidate = httpUri(value);
        if (candidate == null) {
            return null;
        }
        String candidateHost = normalizedHost(candidate);
        String officialHost = normalizedHost(httpUri(effectiveContext.merchantDomain()));
        if (isShopifyTechnicalHost(candidateHost) && !candidateHost.equals(officialHost)
                || isMcpHost(candidateHost) && !candidateHost.equals(officialHost)
                || protocolPath(candidate.getPath())) {
            return null;
        }
        for (String coordinate : coordinates(effectiveContext)) {
            URI endpoint = httpUri(coordinate);
            if (endpoint == null) {
                continue;
            }
            String endpointHost = normalizedHost(endpoint);
            if (!candidateHost.equals(endpointHost)) {
                continue;
            }
            if (!endpointHost.equals(officialHost)
                    || effectivePort(candidate) == effectivePort(endpoint)
                            && endpointPathMatches(candidate, endpoint)) {
                return null;
            }
        }
        return candidate.toString();
    }

    private static List<String> coordinates(TransportContext context) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (hasText(context.routingDomain())) {
            values.add(context.routingDomain().trim());
        }
        context.endpoints().stream()
                .filter(MerchantProductMessageSanitizer::hasText)
                .map(String::trim)
                .forEach(values::add);
        return List.copyOf(values);
    }

    private static boolean endpointPathMatches(URI candidate, URI endpoint) {
        String candidatePath = normalizedPath(candidate.getPath());
        String endpointPath = normalizedPath(endpoint.getPath());
        if ("/".equals(endpointPath)) {
            return "/".equals(candidatePath);
        }
        return candidatePath.equals(endpointPath) || candidatePath.startsWith(endpointPath + "/");
    }

    private static int effectivePort(URI value) {
        if (value.getPort() >= 0) {
            return value.getPort();
        }
        return "http".equalsIgnoreCase(value.getScheme()) ? 80 : 443;
    }

    private static boolean protocolPath(String value) {
        String path = normalizedPath(value).toLowerCase(Locale.ROOT);
        return PROTOCOL_PATHS.stream()
                .anyMatch(protocolPath ->
                        path.equals(protocolPath) || path.startsWith(protocolPath + "/"));
    }

    private static String normalizedPath(String value) {
        if (!hasText(value)) {
            return "/";
        }
        String trimmed = value.trim();
        return trimmed.length() > 1 && trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    private static boolean isShopifyTechnicalHost(String host) {
        return host.equals("myshopify.com") || host.endsWith(".myshopify.com");
    }

    private static boolean isMcpHost(String host) {
        return host.startsWith("mcp.") || host.contains(".mcp.");
    }

    private static String allowlisted(String value, Set<String> allowed, String fallback) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return fallback;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static URI httpUri(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            return uri.isAbsolute()
                            && "https".equalsIgnoreCase(uri.getScheme())
                            && hasText(uri.getHost())
                            && uri.getUserInfo() == null
                    ? uri
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizedHost(URI value) {
        return value == null || value.getHost() == null
                ? ""
                : value.getHost().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record TransportContext(
            String merchantDomain,
            String routingDomain,
            List<String> endpoints
    ) {
        public TransportContext {
            List<String> safeEndpoints = new ArrayList<>();
            if (endpoints != null) {
                endpoints.stream()
                        .filter(MerchantProductMessageSanitizer::hasText)
                        .map(String::trim)
                        .forEach(safeEndpoints::add);
            }
            endpoints = List.copyOf(safeEndpoints);
        }
    }

    public record SanitizedMessage(
            String type,
            String code,
            String path,
            String contentType,
            String content,
            String severity,
            String presentation,
            String imageUrl,
            String url
    ) {
    }
}
