package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {

    @Test
    void publishesDescriptorsInRegistrationOrder() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                tool("search_catalog"),
                tool("create_shopping_mission"),
                tool("get_user_preferences")
        ));

        assertThat(registry.descriptors())
                .extracting(AgentToolDescriptor::name)
                .containsExactly("search_catalog", "create_shopping_mission", "get_user_preferences");
    }

    @Test
    void refusesToExposeCheckoutCompletionEvenIfAComponentAttemptsToRegisterIt() {
        AgentTool forbidden = tool(new AgentToolDescriptor(
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
        return tool(new AgentToolDescriptor(
                name,
                "Test tool",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.READ
        ));
    }

    private AgentTool tool(AgentToolDescriptor descriptor) {
        return new TestAgentTool(descriptor);
    }

    private record TestAgentTool(AgentToolDescriptor descriptor) implements AgentTool {

        @Override
        public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
            throw new UnsupportedOperationException("Registry tests do not execute tools");
        }
    }
}
