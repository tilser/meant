package com.meant.api.common.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpStatus;

public final class SpringAiAgentModelGateway implements AgentModelGateway {

    private final ChatModel chatModel;
    private final AgentProperties properties;

    public SpringAiAgentModelGateway(ChatModel chatModel, AgentProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public AgentModelResponse turn(
            AgentModelRequest request,
            Consumer<String> textDeltaConsumer,
            BooleanSupplier cancellationRequested
    ) {
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Agent run was cancelled before the model turn");
        }

        List<ToolCallback> callbacks = request.tools().stream()
                .map(SchemaOnlyToolCallback::new)
                .map(ToolCallback.class::cast)
                .toList();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(request.model())
                .temperature(request.temperature())
                .maxTokens(request.maximumOutputTokens())
                .timeout(properties.modelTimeout())
                .streamUsage(true)
                .parallelToolCalls(true)
                .toolCallbacks(callbacks)
                .build();
        Prompt prompt = new Prompt(request.messages().stream().map(this::toSpringMessage).toList(), options);

        StringBuilder text = new StringBuilder();
        ToolCallCollector calls = new ToolCallCollector();
        Long inputTokens = null;
        Long outputTokens = null;
        String finishReason = null;
        String resolvedModel = request.model();
        long deadline = System.nanoTime() + properties.modelTimeout().toNanos();

        try (Stream<ChatResponse> responses = chatModel.stream(prompt)
                .timeout(properties.modelTimeout())
                .toStream()) {
            for (var iterator = responses.iterator(); iterator.hasNext();) {
                ChatResponse response = iterator.next();
                if (cancellationRequested.getAsBoolean()) {
                    throw new CancellationException("Agent run was cancelled during the model turn");
                }
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Agent model exceeded its configured deadline");
                }
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    if (output == null) {
                        throw new IllegalStateException("Agent model returned a generation without output");
                    }
                    String delta = output.getText();
                    if (delta != null && !delta.isEmpty()) {
                        text.append(delta);
                        textDeltaConsumer.accept(delta);
                    }
                    calls.append(output.getToolCalls());
                    if (generation.getMetadata().getFinishReason() != null) {
                        finishReason = generation.getMetadata().getFinishReason();
                    }
                }
                if (response.getMetadata().getModel() != null && !response.getMetadata().getModel().isBlank()) {
                    resolvedModel = response.getMetadata().getModel();
                }
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    if (usage.getPromptTokens() != null) {
                        inputTokens = usage.getPromptTokens().longValue();
                    }
                    if (usage.getCompletionTokens() != null) {
                        outputTokens = usage.getCompletionTokens().longValue();
                    }
                }
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Agent model exceeded its configured deadline");
            }
            if (cancellationRequested.getAsBoolean()) {
                throw new CancellationException("Agent run was cancelled after the model turn");
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AgentException(
                    HttpStatus.BAD_GATEWAY,
                    com.meant.api.common.constant.ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                    "The agent model is temporarily unavailable.",
                    exception
            );
        }

        return new AgentModelResponse(
                text.toString(),
                calls.results(),
                new AgentModelUsage(inputTokens, outputTokens),
                finishReason,
                resolvedModel
        );
    }

    private Message toSpringMessage(AgentModelMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.text());
            case USER -> new UserMessage(message.text());
            case ASSISTANT -> AssistantMessage.builder()
                    .content(message.text())
                    .toolCalls(message.toolCalls().stream()
                            .map(call -> new AssistantMessage.ToolCall(
                                    call.id(),
                                    "function",
                                    call.name(),
                                    call.argumentsJson()
                            ))
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(message.toolResults().stream().map(this::toSpringToolResult).toList())
                    .build();
        };
    }

    private ToolResponseMessage.ToolResponse toSpringToolResult(AgentModelToolResult result) {
        return new ToolResponseMessage.ToolResponse(
                result.toolCallId(),
                result.toolName(),
                result.resultJson()
        );
    }

    private static final class SchemaOnlyToolCallback implements ToolCallback {

        private final ToolDefinition definition;

        private SchemaOnlyToolCallback(AgentModelToolDefinition source) {
            definition = DefaultToolDefinition.builder()
                    .name(source.name())
                    .description(source.description())
                    .inputSchema(source.inputSchemaJson())
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException("Agent tools are executed only by the Meant run coordinator");
        }
    }

    private static final class ToolCallAccumulator {

        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolCallAccumulator(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private void append(String nextId, String nextName, String fragment) {
            if (nextId != null && !nextId.isBlank()) {
                id = nextId;
            }
            if (nextName != null && !nextName.isBlank()) {
                name = nextName;
            }
            if (fragment == null || fragment.isEmpty()) {
                return;
            }
            String existing = arguments.toString();
            if (fragment.equals(existing)) {
                return;
            }
            if (!existing.isEmpty() && existing.startsWith(fragment)) {
                return;
            }
            if (!existing.isEmpty() && fragment.startsWith(existing)) {
                arguments.setLength(0);
            }
            arguments.append(fragment);
        }

        private AgentModelToolCall toCall() {
            return new AgentModelToolCall(id, name, arguments.toString());
        }
    }

    /**
     * OpenAI-compatible providers may omit a tool-call id and name after the first streamed fragment. Spring AI does
     * not expose the provider's tool-call index, so the stable list position is the only lossless key available for
     * those fragments. An explicit id observed later promotes the same accumulator instead of creating a second call.
     */
    private static final class ToolCallCollector {

        private final Map<String, ToolCallAccumulator> calls = new LinkedHashMap<>();
        private final Map<String, String> explicitKeys = new HashMap<>();
        private final Map<Integer, String> positionalKeys = new HashMap<>();
        private int unnamedSequence;

        private void append(List<AssistantMessage.ToolCall> fragments) {
            if (fragments == null || fragments.isEmpty()) {
                return;
            }
            for (int position = 0; position < fragments.size(); position++) {
                AssistantMessage.ToolCall fragment = fragments.get(position);
                if (fragment == null) {
                    continue;
                }
                String id = text(fragment.id());
                String name = text(fragment.name());
                String key = key(position, id, name);
                ToolCallAccumulator accumulator = calls.computeIfAbsent(
                        key,
                        ignored -> new ToolCallAccumulator(id, name)
                );
                accumulator.append(id, name, fragment.arguments());
                if (id != null) {
                    explicitKeys.put(id, key);
                }
                positionalKeys.put(position, key);
            }
        }

        private String key(int position, String id, String name) {
            if (id != null) {
                String explicit = explicitKeys.get(id);
                if (explicit != null) {
                    return explicit;
                }
                String positional = positionalKeys.get(position);
                if (positional != null && canPromote(calls.get(positional), id, name)) {
                    return positional;
                }
                return "id:" + id;
            }
            String positional = positionalKeys.get(position);
            if (positional != null && sameLogicalCall(calls.get(positional), name)) {
                return positional;
            }
            return "unnamed:" + unnamedSequence++;
        }

        private boolean sameLogicalCall(ToolCallAccumulator accumulator, String name) {
            return accumulator != null
                    && (name == null || accumulator.name == null || accumulator.name.equals(name));
        }

        private boolean canPromote(ToolCallAccumulator accumulator, String id, String name) {
            if (accumulator == null || (accumulator.id != null && !accumulator.id.equals(id))) {
                return false;
            }
            return name == null || accumulator.name == null || accumulator.name.equals(name);
        }

        private List<AgentModelToolCall> results() {
            return calls.values().stream().map(ToolCallAccumulator::toCall).toList();
        }

        private String text(String value) {
            return value == null || value.isBlank() ? null : value;
        }
    }
}
