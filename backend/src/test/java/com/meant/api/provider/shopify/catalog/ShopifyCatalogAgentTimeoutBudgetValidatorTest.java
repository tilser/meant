package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.properties.RestClientProperties;
import com.meant.api.module.agent.properties.AgentProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyCatalogAgentTimeoutBudgetValidatorTest {

    @Test
    void acceptsTheExactCompleteSearchBudget() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(360), Duration.ofSeconds(45), Duration.ofSeconds(180)),
                catalogProperties(true, true, Duration.ofSeconds(10))
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAToolDeadlineThatCannotContainAllSequentialNetworkCalls() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(360), Duration.ofSeconds(45), Duration.ofSeconds(179)),
                catalogProperties(true, true, Duration.ofSeconds(10))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.agent.tool-deadline")
                .hasMessageContaining("PT3M")
                .hasMessageContaining("7 Shopify discovery/catalog deadline");
    }

    @Test
    void rejectsARunDeadlineThatCannotContainFourOuterModelAttemptsAndTheToolBudget() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(359), Duration.ofSeconds(45), Duration.ofSeconds(180)),
                catalogProperties(true, true, Duration.ofSeconds(10))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.agent.run-deadline")
                .hasMessageContaining("PT6M")
                .hasMessageContaining("4 outer model attempts");
    }

    @Test
    void skipsTheSearchBudgetWhenAgentSearchIsDisabled() {
        var validator = validator(
                agentProperties(false, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                catalogProperties(true, true, Duration.ofSeconds(10))
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void skipsTheSearchBudgetWhenTheCatalogSourceIsDisabled() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                catalogProperties(false, true, Duration.ofSeconds(10))
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsTheExactPinnedRouteSearchBudgetWhenRuntimeDiscoveryIsDisabled() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(350), Duration.ofSeconds(45), Duration.ofSeconds(170)),
                catalogProperties(true, false, Duration.ofSeconds(10))
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPinnedRouteToolDeadlineThatCannotContainQualificationAndCatalog() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(350), Duration.ofSeconds(45), Duration.ofSeconds(169)),
                catalogProperties(true, false, Duration.ofSeconds(10))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.agent.tool-deadline")
                .hasMessageContaining("PT2M50S")
                .hasMessageContaining("6 Shopify discovery/catalog deadline");
    }

    @Test
    void rejectsAPinnedRouteRunDeadlineThatCannotContainTheCompleteSearch() {
        var validator = validator(
                agentProperties(true, Duration.ofSeconds(349), Duration.ofSeconds(45), Duration.ofSeconds(170)),
                catalogProperties(true, false, Duration.ofSeconds(10))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.agent.run-deadline")
                .hasMessageContaining("PT5M50S")
                .hasMessageContaining("4 outer model attempts");
    }

    @Test
    void rejectsConfiguredAgentDeadlinesThatCannotBeScheduledInNanoseconds() {
        var validator = validator(
                agentProperties(
                        true,
                        Duration.ofDays(1_000_000),
                        Duration.ofSeconds(45),
                        Duration.ofSeconds(180)
                ),
                catalogProperties(true, true, Duration.ofSeconds(10))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.agent.run-deadline")
                .hasMessageContaining("nanosecond deadline capacity");
    }

    @Test
    void rejectsAPathologicalComputedCatalogBudgetInsteadOfOverflowing() {
        var validator = validator(
                agentProperties(
                        true,
                        Duration.ofSeconds(360),
                        Duration.ofSeconds(45),
                        Duration.ofSeconds(180)
                ),
                catalogProperties(true, true, Duration.ofNanos(Long.MAX_VALUE))
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Computed Shopify catalog tool budget")
                .hasMessageContaining("nanosecond deadline capacity");
    }

    private ShopifyCatalogAgentTimeoutBudgetValidator validator(
            AgentProperties agentProperties,
            ShopifyGlobalCatalogProperties catalogProperties
    ) {
        return new ShopifyCatalogAgentTimeoutBudgetValidator(
                agentProperties,
                new RestClientProperties(5_000, 30_000),
                catalogProperties
        );
    }

    private AgentProperties agentProperties(
            boolean enabled,
            Duration runDeadline,
            Duration modelTimeout,
            Duration toolDeadline
    ) {
        return new AgentProperties(
                enabled,
                "primary-model",
                "fallback-model",
                "https://example.test/v1",
                "test-key",
                "Meant Test",
                "https://example.test",
                "test-v1",
                "test-v1",
                0,
                1024,
                8,
                20,
                6,
                4,
                40,
                64_000,
                24_000,
                2,
                runDeadline,
                modelTimeout,
                toolDeadline,
                Duration.ofMinutes(2),
                Duration.ofSeconds(5),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(5)
        );
    }

    private ShopifyGlobalCatalogProperties catalogProperties(
            boolean discoveryEnabled,
            boolean runtimeDiscoveryEnabled,
            Duration requestDeadline
    ) {
        return new ShopifyGlobalCatalogProperties(
                discoveryEnabled,
                runtimeDiscoveryEnabled,
                URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
                Duration.ofMinutes(15),
                10,
                50,
                50,
                200,
                16,
                Duration.ofSeconds(2),
                Duration.ofSeconds(8),
                requestDeadline,
                3,
                Duration.ofSeconds(30)
        );
    }
}
