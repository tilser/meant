package com.meant.api.plugin.catalog.shopify;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** One configuration boundary for Shopify Global Catalog identity, protocol, limits, and resilience. */
@Validated
@ConfigurationProperties(prefix = "shopify.global-catalog")
public record ShopifyGlobalCatalogProperties(
        @NotNull URI endpoint,
        @NotEmpty Set<@NotBlank String> allowedHosts,
        @NotBlank String sourceIdentity,
        @NotBlank String protocolVersion,
        @NotBlank String extensionId,
        @NotBlank String extensionVersion,
        @NotNull URI extensionSpec,
        @NotNull URI extensionSchema,
        @NotEmpty Set<@NotBlank String> requiredScopes,
        @NotBlank String view,
        @Min(1) @Max(50) int defaultResultLimit,
        @Min(1) @Max(50) int maximumResultLimit,
        @Min(1) @Max(50) int maximumLookupIds,
        @Min(1) int maximumCandidates,
        @Min(1) int maximumConcurrentRequests,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration requestDeadline,
        @Min(1) int circuitFailureThreshold,
        @NotNull Duration circuitOpenDuration
) {

    public ShopifyGlobalCatalogProperties {
        allowedHosts = allowedHosts == null ? Set.of() : Set.copyOf(allowedHosts);
        requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
    }

    @AssertTrue(message = "endpoint must be an allowlisted HTTPS endpoint without credentials or a fragment")
    public boolean hasSafeEndpoint() {
        return endpoint != null
                && endpoint.isAbsolute()
                && "https".equalsIgnoreCase(endpoint.getScheme())
                && endpoint.getHost() != null
                && endpoint.getUserInfo() == null
                && endpoint.getFragment() == null
                && allowedHosts.stream()
                        .filter(host -> host != null)
                        .map(host -> host.trim().toLowerCase(Locale.ROOT))
                        .anyMatch(endpoint.getHost().toLowerCase(Locale.ROOT)::equals);
    }

    @AssertTrue(message = "defaultResultLimit must not exceed maximumResultLimit")
    public boolean hasConsistentLimits() {
        return defaultResultLimit <= maximumResultLimit;
    }

    @AssertTrue(message = "timeouts and circuit open duration must be positive")
    public boolean hasPositiveDurations() {
        return positive(connectTimeout)
                && positive(readTimeout)
                && positive(requestDeadline)
                && positive(circuitOpenDuration);
    }

    @AssertTrue(message = "extension metadata URIs must be absolute")
    public boolean hasAbsoluteExtensionMetadata() {
        return extensionSpec != null && extensionSpec.isAbsolute()
                && extensionSchema != null && extensionSchema.isAbsolute();
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
