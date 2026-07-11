package com.meant.api.provider.shopify.auth;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded, metadata-only metrics for merchant-scoped Shopify operations. */
@Component
@RequiredArgsConstructor
public class ShopifyCommerceMetrics {
    private final MeterRegistry registry;

    public void record(CartRoutingTarget target, String operation, String outcome, long elapsedNanos) {
        String integration = target.merchantIntegrationId() == null ? "external_merchant" : "managed_integration";
        String safeOperation = boundedOperation(operation);
        String safeOutcome = boundedOutcome(outcome);
        registry.counter("commerce.provider.calls",
                "provider", "shopify", "integration", integration,
                "operation", safeOperation, "outcome", safeOutcome).increment();
        Timer.builder("commerce.provider.duration")
                .tag("provider", "shopify").tag("integration", integration)
                .tag("operation", safeOperation).tag("outcome", safeOutcome)
                .register(registry).record(Duration.ofNanos(Math.max(0, elapsedNanos)));
    }

    public void recordStateObservation(CartRoutingTarget target, String operation, String status) {
        registry.counter("commerce.provider.state_observation",
                "provider", "shopify",
                "integration", target.merchantIntegrationId() == null ? "external_merchant" : "managed_integration",
                "operation", boundedOperation(operation),
                "status", boundedStatus(status)).increment();
    }

    private String boundedOperation(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "create_cart", "get_cart", "update_cart", "cancel_cart",
                    "create_checkout", "get_checkout", "update_checkout", "cancel_checkout" -> normalized;
            default -> "unknown";
        };
    }

    private String boundedOutcome(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "success", "unauthorized", "forbidden", "not_found", "conflict", "unprocessable",
                    "rate_limited", "server_failure", "timeout", "malformed_response", "invalid_request" -> normalized;
            default -> "unknown";
        };
    }

    private String boundedStatus(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "incomplete", "requires_escalation", "ready_for_complete", "complete_in_progress",
                    "processing", "completed", "canceled", "cancelled" -> normalized;
            default -> "unknown";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
