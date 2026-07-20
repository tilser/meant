package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentCartPrepareToolTest {

    @Test
    void omitsTheFailureCountWhenEveryRouteSucceeds() {
        assertThat(AgentCartPrepareTool.safeSummary(1, 0)).isEqualTo("Prepared 1 cart(s).");
    }

    @Test
    void retainsAVisibleFailureCountForPartialResults() {
        assertThat(AgentCartPrepareTool.safeSummary(1, 1))
                .isEqualTo("Prepared 1 cart(s); 1 route(s) failed.");
    }
}
