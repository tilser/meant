package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Metadata-only agent telemetry. Prompts, arguments, results, identifiers, and checkout URLs are never tags. */
@Component
@RequiredArgsConstructor
public class AgentMetrics {

    private static final Set<String> REFERENCE_TOOLS = Set.of(
            "get_product", "find_similar_products", "compare_products", "pin_product", "unpin_product",
            "watch_product", "unwatch_product", "get_product_reviews", "find_discount_codes",
            "pick_recommended_product", "prepare_carts", "add_cart_line", "update_cart_line",
            "remove_cart_line", "prepare_checkout", "get_checkout", "update_checkout"
    );

    private final MeterRegistry registry;

    public void run(AgentRunStatus status, String model, String failureCode) {
        registry.counter(
                "commerce.agent.runs",
                "status", status.name().toLowerCase(Locale.ROOT),
                "model", bounded(model, "unknown_model"),
                "failure", bounded(failureCode, "none")
        ).increment();
    }

    public void modelTurn(String model, String outcome, long elapsedNanos) {
        registry.counter(
                "commerce.agent.model.turns",
                "model", bounded(model, "unknown_model"),
                "outcome", bounded(outcome, "unknown")
        ).increment();
        Timer.builder("commerce.agent.model.duration")
                .tag("model", bounded(model, "unknown_model"))
                .tag("outcome", bounded(outcome, "unknown"))
                .register(registry)
                .record(Duration.ofNanos(Math.max(0, elapsedNanos)));
    }

    public void modelUsage(String model, Long inputTokens, Long outputTokens) {
        String safeModel = bounded(model, "unknown_model");
        if (inputTokens != null && inputTokens > 0) {
            registry.counter("commerce.agent.model.tokens", "model", safeModel, "direction", "input")
                    .increment(inputTokens);
        }
        if (outputTokens != null && outputTokens > 0) {
            registry.counter("commerce.agent.model.tokens", "model", safeModel, "direction", "output")
                    .increment(outputTokens);
        }
    }

    public void clarification(String model) {
        registry.counter(
                "commerce.agent.clarifications",
                "model", bounded(model, "unknown_model")
        ).increment();
    }

    public void firstUsefulProposal(String artifactType, Duration latency) {
        Timer.builder("commerce.agent.first_useful_proposal.duration")
                .tag("artifact", bounded(artifactType, "unknown"))
                .register(registry)
                .record(latency.isNegative() ? Duration.ZERO : latency);
    }

    public void tool(String toolName, AgentToolRisk risk, String outcome, long elapsedMilliseconds) {
        String safeTool = bounded(toolName, "unknown_tool");
        String safeOutcome = bounded(outcome, "unknown");
        registry.counter(
                "commerce.agent.tool.calls",
                "tool", safeTool,
                "risk", risk == null ? "unknown" : risk.name().toLowerCase(Locale.ROOT),
                "outcome", safeOutcome
        ).increment();
        Timer.builder("commerce.agent.tool.duration")
                .tag("tool", safeTool)
                .tag("risk", risk == null ? "unknown" : risk.name().toLowerCase(Locale.ROOT))
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, elapsedMilliseconds)));
        if (REFERENCE_TOOLS.contains(toolName)) {
            registry.counter(
                    "commerce.agent.reference_resolution",
                    "tool", safeTool,
                    "outcome", referenceOutcome(outcome)
            ).increment();
        }
        if ("find_similar_products".equals(toolName)) {
            registry.counter(
                    "commerce.agent.inventory_anchor",
                    "outcome", referenceOutcome(outcome)
            ).increment();
        }
        if (toolName != null && (toolName.startsWith("prepare_cart")
                || toolName.contains("cart_line")
                || toolName.contains("checkout"))) {
            registry.counter(
                    "commerce.agent.commerce_stage",
                    "stage", toolName.contains("checkout") ? "checkout" : "cart",
                    "outcome", safeOutcome
            ).increment();
        }
    }

    private String referenceOutcome(String outcome) {
        if (outcome == null) {
            return "unknown";
        }
        return switch (outcome.toLowerCase(Locale.ROOT)) {
            case "completed", "replayed" -> "resolved";
            case "rejected" -> "rejected";
            case "uncertain" -> "uncertain";
            default -> "failed";
        };
    }

    private String bounded(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 80));
    }
}
