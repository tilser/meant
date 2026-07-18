package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentToolRisk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentMetricsTest {

    @Test
    void recordsQualityLatencyTokenAndCommerceSignalsWithoutUserIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.modelUsage("openai/test-model", 120L, 30L);
        metrics.clarification("openai/test-model");
        metrics.firstUsefulProposal("PRODUCT", Duration.ofMillis(250));
        metrics.tool("prepare_carts", AgentToolRisk.REVERSIBLE_MUTATION, "completed", 40);

        assertThat(registry.get("commerce.agent.model.tokens")
                .tags("model", "openai/test-model", "direction", "input")
                .counter().count()).isEqualTo(120);
        assertThat(registry.get("commerce.agent.clarifications").counter().count()).isEqualTo(1);
        assertThat(registry.get("commerce.agent.first_useful_proposal.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("commerce.agent.reference_resolution")
                .tags("tool", "prepare_carts", "outcome", "resolved")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("commerce.agent.commerce_stage")
                .tags("stage", "cart", "outcome", "completed")
                .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getKey().contains("user")
                        || tag.getKey().contains("conversation")
                        || tag.getKey().contains("run")));
    }
}
