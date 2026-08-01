package com.meant.api.provider.shopify.catalog;

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
        /** Enables the Shopify Global Catalog as a federated discovery source. */
        boolean discoveryEnabled,
        /** Enables live {@code /.well-known/ucp} and MCP {@code tools/list} route/schema discovery. */
        boolean runtimeDiscoveryEnabled,
        @NotNull URI endpoint,
        @NotEmpty Set<@NotBlank String> allowedHosts,
        @NotBlank String protocolVersion,
        @NotNull Duration discoveryCacheTtl,
        @Min(1) @Max(50) int defaultResultLimit,
        @Min(1) @Max(50) int maximumResultLimit,
        @Min(1) @Max(50) int maximumLookupIds,
        /**
         * Bounds normalized provider candidates to twice the public 100-product ranking window.
         * The second window leaves room for normalization and exact grouping without permitting
         * unbounded cursor traversal or timeout multiplication.
         */
        @Min(1) @Max(MAXIMUM_CONFIGURED_CANDIDATES) int maximumCandidates,
        @Min(1) int maximumConcurrentRequests,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration requestDeadline,
        @Min(1) int circuitFailureThreshold,
        @NotNull Duration circuitOpenDuration
) {

    private static final String SOURCE_IDENTITY = "SHOPIFY_GLOBAL_CATALOG";
    private static final Set<String> REQUIRED_SCOPES = Set.of("read_global_api_catalog_search");
    private static final String VIEW = "offer";
    public static final int MAXIMUM_CONFIGURED_CANDIDATES = 200;
    public static final int MAXIMUM_VERIFIED_CANDIDATES = 100;
    static final Duration FEDERATION_SCHEDULER_RESERVE = Duration.ofSeconds(1);

    public ShopifyGlobalCatalogProperties {
        allowedHosts = allowedHosts == null ? Set.of() : Set.copyOf(allowedHosts);
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

    @AssertTrue(message = "timeouts, discovery cache TTL, and circuit open duration must be positive")
    public boolean hasPositiveDurations() {
        return positive(connectTimeout)
                && positive(readTimeout)
                && positive(requestDeadline)
                && positive(discoveryCacheTtl)
                && positive(circuitOpenDuration);
    }

    @AssertTrue(message = "catalog pagination must remain within the configured candidate capacity")
    public boolean hasBoundedPagination() {
        try {
            return maximumSearchPages() >= 1;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    @AssertTrue(message = "catalog source timeout must fit in nanosecond deadline arithmetic")
    public boolean hasNanosSafeDiscoverySourceTimeout() {
        try {
            discoverySourceTimeout();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public String sourceIdentity() {
        return SOURCE_IDENTITY;
    }

    public Set<String> requiredScopes() {
        return REQUIRED_SCOPES;
    }

    public String view() {
        return VIEW;
    }

    /**
     * Contains the worst-case sequential remote path plus time for federation timeout dispatch.
     *
     * <p>Live discovery owns one request deadline for profile/schema verification before the
     * provider owns up to {@link #maximumSearchPages()} deadlines for {@code search_catalog} and
     * {@link #maximumVerificationBatches()} deadlines for fail-closed {@code lookup_catalog}
     * verification. A pinned route skips the discovery deadline.</p>
     */
    public Duration discoverySourceTimeout() {
        if (!positive(requestDeadline)) {
            throw new IllegalStateException("Shopify catalog request deadline must be positive");
        }
        long sequentialRequestDeadlines = (long) maximumSearchPages()
                + maximumVerificationBatches()
                + (runtimeDiscoveryEnabled ? 1L : 0L);
        try {
            Duration timeout = requestDeadline
                    .multipliedBy(sequentialRequestDeadlines)
                    .plus(FEDERATION_SCHEDULER_RESERVE);
            timeout.toNanos();
            return timeout;
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Shopify catalog source timeout exceeds nanosecond deadline capacity",
                    exception
            );
        }
    }

    /**
     * Bounds cursor traversal to the configured provider candidate capacity.
     */
    public int maximumSearchPages() {
        if (maximumCandidates < 1 || maximumCandidates > MAXIMUM_CONFIGURED_CANDIDATES
                || maximumResultLimit < 1 || maximumResultLimit > 50) {
            throw new IllegalStateException(
                    "Shopify catalog pagination limits are outside the supported range");
        }
        long pages = Math.ceilDiv((long) maximumCandidates, (long) maximumResultLimit);
        try {
            return Math.toIntExact(pages);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Shopify catalog page count exceeds integer capacity", exception);
        }
    }

    /** Bounds fail-closed lookup verification to the public ranked product window. */
    public int maximumVerificationBatches() {
        if (maximumLookupIds < 1 || maximumLookupIds > 50) {
            throw new IllegalStateException(
                    "Shopify catalog lookup limit is outside the supported range");
        }
        return Math.toIntExact(Math.ceilDiv(
                (long) MAXIMUM_VERIFIED_CANDIDATES,
                (long) maximumLookupIds
        ));
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
