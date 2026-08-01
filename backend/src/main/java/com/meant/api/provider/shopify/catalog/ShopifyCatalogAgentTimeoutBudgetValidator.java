package com.meant.api.provider.shopify.catalog;

import com.meant.api.common.properties.RestClientProperties;
import com.meant.api.module.agent.properties.AgentProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates the complete wall-clock budget for an agent-driven Shopify catalog search.
 *
 * <p>The agent and OpenRouter clients use different timeout properties, so this relationship
 * cannot be validated correctly inside either configuration-properties record by itself.</p>
 */
@Component
@RequiredArgsConstructor
public class ShopifyCatalogAgentTimeoutBudgetValidator {

    static final int MAXIMUM_OUTER_MODEL_ATTEMPTS = 4;
    static final int MAXIMUM_SEQUENTIAL_OPENROUTER_READS = 3;
    static final Duration ORCHESTRATION_RESERVE = Duration.ofSeconds(20);

    private final AgentProperties agentProperties;
    private final RestClientProperties restClientProperties;
    private final ShopifyGlobalCatalogProperties catalogProperties;

    @PostConstruct
    void validate() {
        if (!agentProperties.enabled() || !catalogProperties.discoveryEnabled()) {
            return;
        }
        requireNanosCapacity(agentProperties.modelTimeout(), "commerce.agent.model-timeout");
        requireNanosCapacity(agentProperties.toolDeadline(), "commerce.agent.tool-deadline");
        requireNanosCapacity(agentProperties.runDeadline(), "commerce.agent.run-deadline");
        Duration openRouterReadTimeout =
                Duration.ofMillis(restClientProperties.readTimeoutMilliseconds());
        int maximumSequentialShopifyDeadlines = catalogProperties.maximumSearchPages()
                + catalogProperties.maximumVerificationBatches()
                + (catalogProperties.runtimeDiscoveryEnabled() ? 1 : 0);
        Duration requiredToolDeadline;
        try {
            requiredToolDeadline = openRouterReadTimeout
                    .multipliedBy(MAXIMUM_SEQUENTIAL_OPENROUTER_READS)
                    .plus(catalogProperties.requestDeadline()
                            .multipliedBy(maximumSequentialShopifyDeadlines))
                    .plus(ORCHESTRATION_RESERVE);
            requiredToolDeadline.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Computed Shopify catalog tool budget exceeds nanosecond deadline capacity",
                    exception
            );
        }
        if (agentProperties.toolDeadline().compareTo(requiredToolDeadline) < 0) {
            throw new IllegalStateException(
                    ("commerce.agent.tool-deadline must be at least %s to contain %d sequential "
                            + "OpenRouter read timeouts, %d Shopify discovery/catalog deadline(s), "
                            + "and orchestration reserve")
                            .formatted(
                                    requiredToolDeadline,
                                    MAXIMUM_SEQUENTIAL_OPENROUTER_READS,
                                    maximumSequentialShopifyDeadlines)
            );
        }

        Duration requiredRunDeadline;
        try {
            requiredRunDeadline = agentProperties.modelTimeout()
                    .multipliedBy(MAXIMUM_OUTER_MODEL_ATTEMPTS)
                    .plus(requiredToolDeadline);
            requiredRunDeadline.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Computed Shopify catalog run budget exceeds nanosecond deadline capacity",
                    exception
            );
        }
        if (agentProperties.runDeadline().compareTo(requiredRunDeadline) < 0) {
            throw new IllegalStateException(
                    "commerce.agent.run-deadline must be at least %s to contain %d outer model attempts, "
                            .formatted(
                                    requiredRunDeadline,
                                    MAXIMUM_OUTER_MODEL_ATTEMPTS)
                            + "the complete qualification/catalog tool budget, and orchestration reserve"
            );
        }
    }

    private void requireNanosCapacity(Duration duration, String property) {
        if (duration == null) {
            throw new IllegalStateException(property + " is required");
        }
        try {
            duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    property + " exceeds nanosecond deadline capacity",
                    exception
            );
        }
    }
}
