package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.transport.dto.ShopifyTokenScopeDecision;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

public record ShopifyBearerAuthenticationResult(
        ShopifyTokenScopeDecision decision,
        Optional<ShopifyBearerHeader> header
) {

    public ShopifyBearerAuthenticationResult {
        decision = Objects.requireNonNull(decision, "decision");
        header = header == null ? Optional.empty() : header;
    }

    public void applyTo(HttpHeaders headers) {
        ShopifyBearerHeader bearerHeader = header.orElseThrow(
                () -> new IllegalStateException("Shopify bearer authentication is unavailable: " + decision.availability())
        );
        bearerHeader.applyTo(headers);
    }

    @Override
    public String toString() {
        return "ShopifyBearerAuthenticationResult[decision=%s, header=%s]"
                .formatted(decision, header.isPresent() ? "[redacted]" : "unavailable");
    }
}
