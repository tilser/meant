package com.meant.api.provider.shopify.auth;

import com.meant.api.provider.shopify.auth.ShopifyTokenLimits;
import com.meant.api.provider.shopify.auth.ShopifyTokenMetadata;
import com.meant.api.provider.shopify.auth.ShopifyTokenResponse;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class ShopifyTokenProvider {

    private static final Pattern SCOPE_SEPARATOR = Pattern.compile("[\\s,]+");
    private static final TypeReference<Map<String, Long>> LIMITS_TYPE = new TypeReference<>() {
    };

    private final ShopifyTokenClient tokenClient;
    private final ShopifyAgentAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object refreshMonitor = new Object();
    private final AtomicLong generations = new AtomicLong();

    private volatile ShopifyAccessToken cachedToken;
    private CompletableFuture<ShopifyAccessToken> inFlightRefresh;

    @Autowired
    ShopifyTokenProvider(
            ShopifyTokenClient tokenClient,
            ShopifyAgentAuthProperties properties,
            ObjectMapper objectMapper
    ) {
        this(tokenClient, properties, objectMapper, Clock.systemUTC());
    }

    ShopifyTokenProvider(
            ShopifyTokenClient tokenClient,
            ShopifyAgentAuthProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.tokenClient = Objects.requireNonNull(tokenClient, "tokenClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ShopifyAccessToken currentToken() {
        requireEnabled();
        Instant now = clock.instant();
        ShopifyAccessToken current = cachedToken;
        if (isUsable(current, now)) {
            return current;
        }

        return refreshSingleFlight(null);
    }

    ShopifyAccessToken refreshAfterUnauthorized(long rejectedGeneration) {
        requireEnabled();
        return refreshSingleFlight(rejectedGeneration);
    }

    private ShopifyAccessToken refreshSingleFlight(Long rejectedGeneration) {
        CompletableFuture<ShopifyAccessToken> refresh;
        boolean owner = false;
        synchronized (refreshMonitor) {
            ShopifyAccessToken current = cachedToken;
            if (rejectedGeneration == null && isUsable(current, clock.instant())) {
                return current;
            }
            if (rejectedGeneration != null && current != null
                    && current.generation() != rejectedGeneration
                    && isUsable(current, clock.instant())) {
                return current;
            }
            if (inFlightRefresh == null) {
                inFlightRefresh = new CompletableFuture<>();
                if (rejectedGeneration != null) {
                    cachedToken = null;
                }
                owner = true;
            }
            refresh = inFlightRefresh;
        }

        if (owner) {
            completeRefresh(refresh);
        }
        return await(refresh);
    }

    private void completeRefresh(CompletableFuture<ShopifyAccessToken> refresh) {
        try {
            ShopifyAccessToken token = acquire(clock.instant());
            synchronized (refreshMonitor) {
                cachedToken = token;
                inFlightRefresh = null;
            }
            refresh.complete(token);
        } catch (Throwable throwable) {
            synchronized (refreshMonitor) {
                inFlightRefresh = null;
            }
            refresh.completeExceptionally(throwable);
            if (throwable instanceof Error error) {
                throw error;
            }
        }
    }

    private ShopifyAccessToken await(CompletableFuture<ShopifyAccessToken> refresh) {
        try {
            return refresh.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new ShopifyTransientException("Shopify token refresh did not complete");
        }
    }

    private ShopifyAccessToken acquire(Instant acquiredAt) {
        ShopifyTokenResponse response = tokenClient.exchangeClientCredentials();
        JwtMetadata jwtMetadata = jwtMetadata(response.accessToken());
        Instant expiresAt = expiry(response, jwtMetadata, acquiredAt);
        if (!expiresAt.isAfter(acquiredAt)) {
            throw new ShopifyMalformedTokenResponseException("Shopify token was already expired when issued");
        }

        Set<String> responseScopes = scopes(response.scope());
        Set<String> effectiveScopes = responseScopes.isEmpty() ? jwtMetadata.scopes() : responseScopes;
        ShopifyTokenMetadata metadata = new ShopifyTokenMetadata(
                expiresAt,
                effectiveScopes,
                new ShopifyTokenLimits(jwtMetadata.limits())
        );
        ShopifyAccessToken refreshed = new ShopifyAccessToken(
                response.accessToken(),
                generations.incrementAndGet(),
                metadata
        );
        return refreshed;
    }

    private Instant expiry(ShopifyTokenResponse response, JwtMetadata jwtMetadata, Instant acquiredAt) {
        Instant responseExpiry = response.expiresIn() == null
                ? null
                : safeExpiry(acquiredAt, Duration.ofSeconds(response.expiresIn()));
        Instant jwtExpiry = jwtMetadata.expiresAt().orElse(null);
        if (responseExpiry != null && jwtExpiry != null) {
            return responseExpiry.isBefore(jwtExpiry) ? responseExpiry : jwtExpiry;
        }
        if (responseExpiry != null) {
            return responseExpiry;
        }
        if (jwtExpiry != null) {
            return jwtExpiry;
        }
        return safeExpiry(acquiredAt, properties.fallbackTokenTtl());
    }

    private Instant safeExpiry(Instant acquiredAt, Duration lifetime) {
        try {
            return acquiredAt.plus(lifetime);
        } catch (ArithmeticException | DateTimeException exception) {
            throw new ShopifyMalformedTokenResponseException("Shopify token expiry was outside the supported range");
        }
    }

    private boolean isUsable(ShopifyAccessToken token, Instant now) {
        return token != null && token.metadata().expiresAt().isAfter(now.plus(properties.refreshSkew()));
    }

    private JwtMetadata jwtMetadata(String accessToken) {
        // Shopify documents these claims as issued-token metadata. Decoding them here does not verify
        // the JWT signature and must not be used as independent proof of authorization.
        if (!StringUtils.hasText(accessToken)) {
            return JwtMetadata.empty();
        }
        String[] segments = accessToken.split("\\.", -1);
        if (segments.length < 2 || segments[1].isBlank()) {
            return JwtMetadata.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(segments[1]);
            JsonNode claims = objectMapper.readTree(decoded);
            return new JwtMetadata(
                    expiryClaim(claims.path("exp")),
                    scopeClaims(claims.path("scopes")),
                    limitClaims(claims.path("limits"))
            );
        } catch (IllegalArgumentException | JacksonException exception) {
            return JwtMetadata.empty();
        }
    }

    private Optional<Instant> expiryClaim(JsonNode exp) {
        if (exp == null || !exp.isNumber()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(exp.longValue()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Set<String> scopeClaims(JsonNode scopes) {
        if (scopes == null || scopes.isMissingNode() || scopes.isNull()) {
            return Set.of();
        }
        if (scopes.isTextual()) {
            return scopes(scopes.textValue());
        }
        if (!scopes.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode scope : scopes) {
            if (scope.isTextual()) {
                values.addAll(scopes(scope.textValue()));
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private Map<String, Long> limitClaims(JsonNode limits) {
        if (limits == null || !limits.isObject()) {
            return Map.of();
        }
        try {
            Map<String, Long> values = objectMapper.convertValue(limits, LIMITS_TYPE);
            if (values == null) {
                return Map.of();
            }
            Map<String, Long> clean = new TreeMap<>();
            values.forEach((key, value) -> {
                if (key != null && value != null) {
                    clean.put(key, value);
                }
            });
            return Collections.unmodifiableMap(clean);
        } catch (IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private Set<String> scopes(String scopeText) {
        if (!StringUtils.hasText(scopeText)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String scope : SCOPE_SEPARATOR.split(scopeText.trim())) {
            if (!scope.isBlank()) {
                values.add(scope);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ShopifyAuthenticationException("Shopify agent authentication is disabled");
        }
    }

    private record JwtMetadata(
            Optional<Instant> expiresAt,
            Set<String> scopes,
            Map<String, Long> limits
    ) {

        private static JwtMetadata empty() {
            return new JwtMetadata(Optional.empty(), Set.of(), Map.of());
        }
    }
}
