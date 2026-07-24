package com.meant.api.provider.geonames;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "geonames")
public record GeoNamesProperties(
        String username,
        @NotNull URI baseUrl,
        @NotNull Duration cacheTtl,
        @Positive long maximumCacheEntries
) {

    @AssertTrue(message = "baseUrl must be an absolute HTTPS URI without credentials")
    public boolean hasValidBaseUrl() {
        return baseUrl != null
                && baseUrl.isAbsolute()
                && "https".equalsIgnoreCase(baseUrl.getScheme())
                && StringUtils.hasText(baseUrl.getHost())
                && baseUrl.getUserInfo() == null;
    }

    @AssertTrue(message = "cacheTtl must be positive")
    public boolean hasValidCacheTtl() {
        return cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(username);
    }

    @Override
    public String toString() {
        return "GeoNamesProperties[username=[redacted], baseUrl=%s, cacheTtl=%s, maximumCacheEntries=%s]"
                .formatted(baseUrl, cacheTtl, maximumCacheEntries);
    }
}
