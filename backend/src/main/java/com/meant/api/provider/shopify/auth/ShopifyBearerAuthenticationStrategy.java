package com.meant.api.provider.shopify.auth;

import com.meant.api.provider.shopify.auth.ShopifyTokenMetadata;
import com.meant.api.provider.shopify.auth.ShopifyTokenScopeDecision;
import com.meant.api.provider.shopify.auth.ShopifyTokenScopeDecision.Availability;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.util.StringUtils;

@Component
public class ShopifyBearerAuthenticationStrategy {

    private final ShopifyTokenProvider tokenProvider;
    private final ShopifyAgentAuthProperties properties;
    private final MeterRegistry meterRegistry;

    public ShopifyBearerAuthenticationStrategy(
            ShopifyTokenProvider tokenProvider,
            ShopifyAgentAuthProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.tokenProvider = Objects.requireNonNull(tokenProvider, "tokenProvider");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    public ShopifyBearerAuthenticationResult prepare(Set<String> requiredScopes) {
        Set<String> required = normalize(requiredScopes);
        if (!properties.isEnabled()) {
            return unavailable(Availability.DISABLED, required, required, Optional.empty());
        }
        return result(tokenProvider.currentToken(), required);
    }

    public ShopifyBearerAuthenticationResult refreshAfterUnauthorized(
            ShopifyBearerAuthenticationResult rejectedAuthentication,
            Set<String> requiredScopes
    ) {
        Set<String> required = normalize(requiredScopes);
        Optional<ShopifyBearerHeader> rejectedHeader = rejectedAuthentication == null
                ? Optional.empty()
                : rejectedAuthentication.header();
        if (rejectedHeader.isEmpty() || !rejectedHeader.orElseThrow().claimUnauthorizedRefresh()) {
            Optional<ShopifyTokenMetadata> metadata = rejectedAuthentication == null
                    ? Optional.empty()
                    : rejectedAuthentication.decision().metadata();
            return unavailable(
                    Availability.UNAUTHORIZED_REFRESH_ALREADY_ATTEMPTED,
                    required,
                    Set.of(),
                    metadata
            );
        }
        try {
            ShopifyBearerAuthenticationResult result = result(
                    tokenProvider.refreshAfterUnauthorized(rejectedHeader.orElseThrow().generation()), required);
            recordRefresh("success");
            return result;
        } catch (RuntimeException exception) {
            recordRefresh("failure");
            throw exception;
        }
    }

    private ShopifyBearerAuthenticationResult result(ShopifyAccessToken token, Set<String> requiredScopes) {
        Set<String> missing = new TreeSet<>(requiredScopes);
        missing.removeAll(token.metadata().scopes());
        if (!missing.isEmpty()) {
            return unavailable(
                    Availability.MISSING_SCOPES,
                    requiredScopes,
                    missing,
                    Optional.of(token.metadata())
            );
        }
        ShopifyTokenScopeDecision decision = new ShopifyTokenScopeDecision(
                Availability.AVAILABLE,
                requiredScopes,
                Set.of(),
                Optional.of(token.metadata())
        );
        return new ShopifyBearerAuthenticationResult(
                decision,
                Optional.of(new ShopifyBearerHeader(token.value(), token.generation()))
        );
    }

    private ShopifyBearerAuthenticationResult unavailable(
            Availability availability,
            Set<String> requiredScopes,
            Set<String> missingScopes,
            Optional<ShopifyTokenMetadata> metadata
    ) {
        return new ShopifyBearerAuthenticationResult(
                new ShopifyTokenScopeDecision(availability, requiredScopes, missingScopes, metadata),
                Optional.empty()
        );
    }

    private Set<String> normalize(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (StringUtils.hasText(scope)) {
                normalized.add(scope.trim());
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private void recordRefresh(String outcome) {
        meterRegistry.counter("commerce.provider.auth_refresh",
                "provider", "shopify", "integration", "token_tier",
                "operation", "authentication", "outcome", outcome).increment();
    }
}
