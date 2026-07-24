package com.meant.api.module.catalog.service.support;

import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Buyer-safe projection helpers for provider-authored canonical catalog content. */
public final class CatalogBuyerPresentation {

    private static final List<String> PROTOCOL_PATHS = List.of(
            "/.well-known/ucp.json",
            "/.well-known/ucp",
            "/api/ucp/mcp",
            "/api/mcp",
            "/mcp"
    );

    private CatalogBuyerPresentation() {
    }

    public static String text(String value, List<ResultProvenance> provenance) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        List<ResultProvenance> sources = safeProvenance(provenance);
        String merchantOrigin = presentationOrigin(sources);
        String sanitized = value;
        for (ResultProvenance source : sources) {
            sanitized = MerchantBuyerTextSanitizer.sanitizeTechnicalEndpoint(
                    sanitized,
                    source.merchantOrigin(),
                    source.externalMerchantDomain()
            );
            sanitized = MerchantBuyerTextSanitizer.sanitizeTechnicalEndpoint(
                    sanitized,
                    source.merchantOrigin(),
                    source.sourceReference().uri() == null
                            ? null
                            : source.sourceReference().uri().toString()
            );
        }
        List<ProtectedOrigin> protectedOrigins = protectVerifiedOrigins(sanitized, sources);
        String buyerSafe = MerchantBuyerTextSanitizer.sanitize(
                protectedOrigins.getFirst().value(),
                merchantOrigin,
                null,
                (String) null
        );
        for (int index = 1; index < protectedOrigins.size(); index++) {
            ProtectedOrigin origin = protectedOrigins.get(index);
            buyerSafe = buyerSafe.replace(origin.token(), origin.value());
        }
        return buyerSafe;
    }

    public static String label(String value, List<ResultProvenance> provenance) {
        String sanitized = text(value, provenance);
        if (sanitized == null || sanitized.isBlank()) {
            return sanitized;
        }
        String trimmed = sanitized.trim();
        return trimmed.equalsIgnoreCase("the merchant") ? "Merchant" : trimmed;
    }

    public static String merchantOrigin(List<ResultProvenance> provenance) {
        return presentationOrigin(safeProvenance(provenance));
    }

    public static URI safeUri(URI value, List<ResultProvenance> provenance) {
        if (value == null
                || value.getHost() == null
                || value.getUserInfo() != null
                || !("https".equalsIgnoreCase(value.getScheme())
                        || "http".equalsIgnoreCase(value.getScheme()))
                || isMcpHost(value.getHost())
                || isUnverifiedShopifyHost(value.getHost(), provenance)
                || isUnverifiedRoutingHost(value.getHost(), provenance)
                || protocolPath(value.getPath())) {
            return null;
        }
        boolean endpointCoordinate = safeProvenance(provenance).stream()
                .map(source -> source.sourceReference().uri())
                .filter(endpoint -> endpoint != null)
                .anyMatch(endpoint -> endpointPathMatches(value, endpoint));
        return endpointCoordinate ? null : value;
    }

    private static List<ResultProvenance> safeProvenance(List<ResultProvenance> provenance) {
        return provenance == null
                ? List.of()
                : provenance.stream().filter(source -> source != null).toList();
    }

    private static String presentationOrigin(List<ResultProvenance> provenance) {
        return provenance.stream()
                .map(ResultProvenance::merchantOrigin)
                .map(MerchantBuyerTextSanitizer::buyerSafeMerchantOrigin)
                .filter(domain -> domain != null)
                .findFirst()
                .orElse(null);
    }

    private static List<ProtectedOrigin> protectVerifiedOrigins(
            String value,
            List<ResultProvenance> provenance
    ) {
        List<ProtectedOrigin> protectedOrigins = new ArrayList<>();
        protectedOrigins.add(new ProtectedOrigin(null, value));
        List<String> origins = provenance.stream()
                .map(ResultProvenance::merchantOrigin)
                .map(MerchantBuyerTextSanitizer::buyerSafeMerchantOrigin)
                .filter(origin -> origin != null)
                .distinct()
                .toList();
        String protectedValue = value;
        for (int index = 0; index < origins.size(); index++) {
            String origin = origins.get(index);
            String token = "__MEANT_VERIFIED_ORIGIN_" + index + "__";
            protectedValue = Pattern.compile(
                            Pattern.quote(origin),
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    )
                    .matcher(protectedValue)
                    .replaceAll(Matcher.quoteReplacement(token));
            protectedOrigins.add(new ProtectedOrigin(token, origin));
        }
        protectedOrigins.set(0, new ProtectedOrigin(null, protectedValue));
        return List.copyOf(protectedOrigins);
    }

    private static boolean endpointPathMatches(URI candidate, URI endpoint) {
        if (candidate.getHost() == null
                || endpoint.getHost() == null
                || !candidate.getHost().equalsIgnoreCase(endpoint.getHost())
                || effectivePort(candidate) != effectivePort(endpoint)) {
            return false;
        }
        String candidatePath = normalizedPath(candidate.getPath());
        String endpointPath = normalizedPath(endpoint.getPath());
        return candidatePath.equals(endpointPath)
                || !"/".equals(endpointPath) && candidatePath.startsWith(endpointPath + "/");
    }

    private static boolean protocolPath(String value) {
        String path = normalizedPath(value).toLowerCase(Locale.ROOT);
        return PROTOCOL_PATHS.stream()
                .anyMatch(protocolPath ->
                        path.equals(protocolPath) || path.startsWith(protocolPath + "/"));
    }

    private static boolean isMcpHost(String value) {
        String host = value.toLowerCase(Locale.ROOT);
        return host.startsWith("mcp.") || host.contains(".mcp.");
    }

    private static boolean isUnverifiedShopifyHost(
            String hostValue,
            List<ResultProvenance> provenance
    ) {
        String host = hostValue.toLowerCase(Locale.ROOT);
        if (!(host.equals("myshopify.com") || host.endsWith(".myshopify.com"))) {
            return false;
        }
        return safeProvenance(provenance).stream()
                .map(ResultProvenance::merchantOrigin)
                .map(MerchantBuyerTextSanitizer::buyerSafeMerchantOrigin)
                .noneMatch(origin -> host.equalsIgnoreCase(origin));
    }

    private static boolean isUnverifiedRoutingHost(
            String hostValue,
            List<ResultProvenance> provenance
    ) {
        String host = hostValue.toLowerCase(Locale.ROOT);
        return safeProvenance(provenance).stream()
                .anyMatch(source -> host.equalsIgnoreCase(source.externalMerchantDomain())
                        && !host.equalsIgnoreCase(
                                MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(source.merchantOrigin())
                        ));
    }

    private static String normalizedPath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String trimmed = value.trim();
        return trimmed.length() > 1 && trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    private static int effectivePort(URI value) {
        if (value.getPort() >= 0) {
            return value.getPort();
        }
        return "http".equalsIgnoreCase(value.getScheme()) ? 80 : 443;
    }

    private record ProtectedOrigin(String token, String value) {
    }
}
