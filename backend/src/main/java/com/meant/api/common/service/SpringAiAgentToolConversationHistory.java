package com.meant.api.common.service;

import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.port.AgentToolConversationHistory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class SpringAiAgentToolConversationHistory implements AgentToolConversationHistory {

    private final ToolCallingManager toolCallingManager;

    public SpringAiAgentToolConversationHistory() {
        this(ToolCallingManager.builder().build());
    }

    SpringAiAgentToolConversationHistory(ToolCallingManager toolCallingManager) {
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public List<AgentModelMessage> afterToolExecution(
            List<AgentModelMessage> conversationHistory,
            String assistantText,
            List<AgentModelToolCall> toolCalls,
            List<AgentModelToolResult> toolResults,
            List<AgentModelToolDefinition> toolDefinitions
    ) {
        if (toolCalls.isEmpty() || toolCalls.size() != toolResults.size()) {
            throw new IllegalArgumentException("Tool calls and results must be non-empty and have matching sizes");
        }

        Map<String, AgentModelToolDefinition> definitionsByName = new LinkedHashMap<>();
        toolDefinitions.forEach(definition -> definitionsByName.putIfAbsent(definition.name(), definition));
        Map<String, List<CompletedToolCall>> completedByName = new LinkedHashMap<>();
        for (int index = 0; index < toolCalls.size(); index++) {
            AgentModelToolCall call = toolCalls.get(index);
            AgentModelToolResult result = toolResults.get(index);
            if (!Objects.equals(call.id(), result.toolCallId())
                    || !Objects.equals(call.name(), result.toolName())) {
                throw new IllegalArgumentException("Tool result does not match its model tool call");
            }
            completedByName.computeIfAbsent(call.name(), ignored -> new ArrayList<>())
                    .add(new CompletedToolCall(call.argumentsJson(), result.resultJson()));
        }

        List<ResultReturningToolCallback> callbacks = completedByName.entrySet().stream()
                .map(entry -> new ResultReturningToolCallback(
                        definitionsByName.getOrDefault(entry.getKey(), fallbackDefinition(entry.getKey())),
                        entry.getValue()
                ))
                .toList();
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(callbacks.stream().map(ToolCallback.class::cast).toList())
                .build();
        Prompt prompt = new Prompt(conversationHistory.stream().map(this::toSpringMessage).toList(), options);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(assistantText)
                .toolCalls(toolCalls.stream().map(this::toSpringToolCall).toList())
                .build();
        ToolExecutionResult execution = toolCallingManager.executeToolCalls(
                prompt,
                new ChatResponse(List.of(new Generation(assistantMessage)))
        );
        callbacks.forEach(ResultReturningToolCallback::requireExhausted);
        return execution.conversationHistory().stream().map(this::fromSpringMessage).toList();
    }

    private AgentModelToolDefinition fallbackDefinition(String toolName) {
        return new AgentModelToolDefinition(
                toolName,
                "Tool requested by the model",
                "{\"type\":\"object\",\"additionalProperties\":true}"
        );
    }

    private Message toSpringMessage(AgentModelMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.text());
            case USER -> new UserMessage(message.text());
            case ASSISTANT -> AssistantMessage.builder()
                    .content(message.text())
                    .toolCalls(message.toolCalls().stream().map(this::toSpringToolCall).toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(message.toolResults().stream().map(this::toSpringToolResult).toList())
                    .build();
        };
    }

    private AgentModelMessage fromSpringMessage(Message message) {
        if (message instanceof SystemMessage systemMessage) {
            return AgentModelMessage.system(systemMessage.getText());
        }
        if (message instanceof UserMessage userMessage) {
            return AgentModelMessage.user(userMessage.getText());
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return AgentModelMessage.assistant(
                    assistantMessage.getText(),
                    assistantMessage.getToolCalls().stream()
                            .map(call -> new AgentModelToolCall(call.id(), call.name(), call.arguments()))
                            .toList()
            );
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            return AgentModelMessage.tools(toolResponseMessage.getResponses().stream()
                    .map(response -> new AgentModelToolResult(
                            response.id(),
                            response.name(),
                            response.responseData()
                    ))
                    .toList());
        }
        throw new IllegalArgumentException("Unsupported Spring AI message type: " + message.getClass().getName());
    }

    private AssistantMessage.ToolCall toSpringToolCall(AgentModelToolCall call) {
        return new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.argumentsJson());
    }

    private ToolResponseMessage.ToolResponse toSpringToolResult(AgentModelToolResult result) {
        return new ToolResponseMessage.ToolResponse(
                result.toolCallId(),
                result.toolName(),
                result.resultJson()
        );
    }

    private record CompletedToolCall(String argumentsJson, String resultJson) {
    }

    private static final class ResultReturningToolCallback implements ToolCallback {

        private final ToolDefinition definition;
        private final ArrayDeque<CompletedToolCall> completedCalls;

        private ResultReturningToolCallback(
                AgentModelToolDefinition source,
                List<CompletedToolCall> completedCalls
        ) {
            definition = DefaultToolDefinition.builder()
                    .name(source.name())
                    .description(source.description())
                    .inputSchema(source.inputSchemaJson())
                    .build();
            this.completedCalls = new ArrayDeque<>(completedCalls);
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            CompletedToolCall completed = completedCalls.pollFirst();
            if (completed == null) {
                throw new IllegalStateException("No completed result is available for tool " + definition.name());
            }
            if (!Objects.equals(completed.argumentsJson(), toolInput)) {
                throw new IllegalStateException("Completed tool arguments do not match the model tool call");
            }
            return completed.resultJson() == null ? "" : completed.resultJson();
        }

        private void requireExhausted() {
            if (!completedCalls.isEmpty()) {
                throw new IllegalStateException("Not all completed results were consumed for tool " + definition.name());
            }
        }
    }
}
