package com.meant.api.module.agent.service.port;

import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import java.util.List;

@FunctionalInterface
public interface AgentToolConversationHistory {

    List<AgentModelMessage> afterToolExecution(
            List<AgentModelMessage> conversationHistory,
            String assistantText,
            List<AgentModelToolCall> toolCalls,
            List<AgentModelToolResult> toolResults,
            List<AgentModelToolDefinition> toolDefinitions
    );
}
