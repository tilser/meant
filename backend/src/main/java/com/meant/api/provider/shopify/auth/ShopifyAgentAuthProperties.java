package com.meant.api.provider.shopify.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shopify.agent-auth")
public record ShopifyAgentAuthProperties(
        @NotNull Boolean enabled,
        @NotBlank String environment,
        String clientId,
        String clientSecret,
        @NotNull URI tokenEndpoint,
        @NotNull Duration refreshSkew,
        @NotNull Duration fallbackTokenTtl
) {

    @AssertTrue(message = "clientId and clientSecret are required when Shopify agent authentication is enabled")
    public boolean hasEnabledCredentials() {
        return !isEnabled() || StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    @AssertTrue(message = "tokenEndpoint must be an absolute HTTP(S) URI and use HTTPS when authentication is enabled")
    public boolean hasValidTokenEndpoint() {
        if (tokenEndpoint == null
                || !tokenEndpoint.isAbsolute()
                || !StringUtils.hasText(tokenEndpoint.getHost())
                || tokenEndpoint.getUserInfo() != null) {
            return false;
        }
        if (isEnabled()) {
            return "https".equalsIgnoreCase(tokenEndpoint.getScheme());
        }
        return "https".equalsIgnoreCase(tokenEndpoint.getScheme())
                || "http".equalsIgnoreCase(tokenEndpoint.getScheme());
    }

    @AssertTrue(message = "refreshSkew and fallbackTokenTtl must be positive, and fallbackTokenTtl must exceed refreshSkew")
    public boolean hasValidTokenTiming() {
        return isPositive(refreshSkew)
                && isPositive(fallbackTokenTtl)
                && fallbackTokenTtl.compareTo(refreshSkew) > 0;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public String toString() {
        return "ShopifyAgentAuthProperties[enabled=%s, environment=%s, clientId=%s, clientSecret=[redacted], "
                .concat("tokenEndpoint=%s, refreshSkew=%s, fallbackTokenTtl=%s]")
                .formatted(enabled, environment, clientId, tokenEndpoint, refreshSkew, fallbackTokenTtl);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
