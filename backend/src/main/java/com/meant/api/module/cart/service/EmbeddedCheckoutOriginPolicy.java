package com.meant.api.module.cart.service;

import com.meant.api.common.properties.CorsProperties;
import com.meant.api.module.checkout.exception.EmbeddedCheckoutException;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmbeddedCheckoutOriginPolicy {
    private final java.util.Set<String> allowedOrigins;

    public EmbeddedCheckoutOriginPolicy(CorsProperties properties) {
        this.allowedOrigins = properties.allowedOrigins().stream()
                .map(this::normalize)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String requireAllowed(String value) {
        String normalized;
        try {
            normalized = normalize(value);
        } catch (RuntimeException exception) {
            throw EmbeddedCheckoutException.forbidden("Embedded checkout origin is invalid");
        }
        if (!allowedOrigins.contains(normalized)) {
            throw EmbeddedCheckoutException.forbidden("Embedded checkout origin is not allowlisted");
        }
        return normalized;
    }

    private String normalize(String value) {
        URI uri = URI.create(value == null ? "" : value.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("Origin must contain only scheme and authority");
        }
        int port = uri.getPort();
        boolean defaultPort = port < 0 || scheme.equals("https") && port == 443 || scheme.equals("http") && port == 80;
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }
}
