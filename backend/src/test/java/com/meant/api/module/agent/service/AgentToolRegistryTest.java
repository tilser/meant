package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {

    @Test
    void refusesToExposeCheckoutCompletionEvenIfAComponentAttemptsToRegisterIt() {
        AgentTool forbidden = mock(AgentTool.class);
        when(forbidden.descriptor()).thenReturn(new AgentToolDescriptor(
                "complete_checkout",
                "Forbidden",
                "{\"type\":\"object\"}",
                "1",
                AgentToolRisk.IRREVERSIBLE_MUTATION
        ));

        assertThatThrownBy(() -> new AgentToolRegistry(List.of(forbidden)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Irreversible commerce tools");
    }

    @Test
    void rejectsDuplicateToolNamesInsteadOfSilentlyChangingTheAllowlist() {
        AgentTool first = tool("search_catalog");
        AgentTool second = tool("search_catalog");

        assertThatThrownBy(() -> new AgentToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate agent tool");
    }

    private AgentTool tool(String name) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.descriptor()).thenReturn(new AgentToolDescriptor(
                name,
                "Test tool",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.READ
        ));
        return tool;
    }
}
