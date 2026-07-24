package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class BuyerSafeCheckoutUrl {

    private static final List<String> PROTOCOL_PATHS = List.of(
            "/.well-known/ucp.json",
            "/.well-known/ucp",
            "/api/ucp/mcp",
            "/api/mcp",
            "/mcp"
    );

    private BuyerSafeCheckoutUrl() {
    }

    static String firstSafe(
            Cart cart,
            MerchantCartProvider provider,
            String... candidates
    ) {
        for (String candidate : candidates) {
            String safe = safe(cart, provider, candidate);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private static String safe(
            Cart cart,
            MerchantCartProvider provider,
            String value
    ) {
        URI candidate = httpsUri(value);
        if (candidate == null
                || isMcpHost(candidate.getHost())
                || protocolPath(candidate.getPath())) {
            return null;
        }
        List<String> endpoints = new ArrayList<>();
        endpoints.add(cart.getEndpoint());
        if (provider != null) {
            endpoints.add(provider.advertisedMcpEndpoint());
            endpoints.add(provider.profileMcpEndpoint());
            provider.integrations().forEach(integration -> endpoints.add(integration.endpoint()));
        }
        boolean endpointCoordinate = endpoints.stream()
                .map(BuyerSafeCheckoutUrl::httpsUri)
                .filter(endpoint -> endpoint != null)
                .anyMatch(endpoint -> endpointPathMatches(candidate, endpoint));
        return endpointCoordinate ? null : candidate.toString();
    }

    private static boolean endpointPathMatches(URI candidate, URI endpoint) {
        if (!candidate.getHost().equalsIgnoreCase(endpoint.getHost())
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

    private static String normalizedPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/";
        }
        String trimmed = value.trim();
        return trimmed.length() > 1 && trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    private static int effectivePort(URI value) {
        return value.getPort() >= 0 ? value.getPort() : 443;
    }

    private static URI httpsUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute()
                            && "https".equalsIgnoreCase(uri.getScheme())
                            && StringUtils.hasText(uri.getHost())
                            && uri.getUserInfo() == null
                    ? uri
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
