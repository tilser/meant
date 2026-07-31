package com.meant.api.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiAgentToolConversationHistoryTest {

    private final SpringAiAgentToolConversationHistory conversationHistory =
            new SpringAiAgentToolConversationHistory();

    @Test
    void usesSpringToolCallingManagerToAppendExecutedCallsAndResults() {
        AgentModelToolDefinition definition = new AgentModelToolDefinition(
                "search_catalog",
                "Search the catalog",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
        );
        List<AgentModelToolCall> calls = List.of(
                new AgentModelToolCall("call-1", "search_catalog", "{\"query\":\"boots\"}"),
                new AgentModelToolCall("call-2", "search_catalog", "{\"query\":\"jackets\"}")
        );
        List<AgentModelToolResult> results = List.of(
                new AgentModelToolResult("call-1", "search_catalog", "{\"products\":[\"boot\"]}"),
                new AgentModelToolResult("call-2", "search_catalog", "{\"products\":[\"jacket\"]}")
        );

        List<AgentModelMessage> history = conversationHistory.afterToolExecution(
                List.of(AgentModelMessage.system("system"), AgentModelMessage.user("find both")),
                "Searching both categories.",
                calls,
                results,
                List.of(definition)
        );

        assertThat(history).hasSize(4);
        assertThat(history.get(2).text()).isEqualTo("Searching both categories.");
        assertThat(history.get(2).toolCalls()).containsExactlyElementsOf(calls);
        assertThat(history.get(3).toolResults()).containsExactlyElementsOf(results);
    }
}
