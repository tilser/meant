package com.meant.api.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

class SpringAiAgentModelGatewayTest {

    @Test
    void streamsTextAndPreservesTerminalMetadataFromAUsageOnlyChunk() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("Hel", List.of(), null, null, null),
                response("lo", List.of(), "STOP", null, null),
                metadataOnlyResponse("openrouter/provider-model", new DefaultUsage(11, 2))
        ));
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofSeconds(1)));
        List<String> deltas = new ArrayList<>();

        var result = gateway.turn(request(List.of()), deltas::add, () -> false);

        assertThat(result.text()).isEqualTo("Hello");
        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(result.finishReason()).isEqualTo("STOP");
        assertThat(result.resolvedModel()).isEqualTo("openrouter/provider-model");
        assertThat(result.usage().inputTokens()).isEqualTo(11L);
        assertThat(result.usage().outputTokens()).isEqualTo(2L);
    }

    @Test
    void assemblesFragmentedIdlessToolArgumentsByStableStreamPosition() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("", List.of(
                        toolCall(null, "search_catalog", "{\"query\":"),
                        toolCall(null, "search_catalog", "{\"query\":")
                ), null, null, null),
                response("", List.of(
                        toolCall("call-search", null, "{\"query\":\"boots\"}"),
                        toolCall(null, null, "\"hats\"}")
                ), "TOOL_CALLS", null, null)
        ));
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofSeconds(1)));

        var result = gateway.turn(request(List.of()), ignored -> { }, () -> false);

        assertThat(result.toolCalls()).containsExactly(
                new AgentModelToolCall("call-search", "search_catalog", "{\"query\":\"boots\"}"),
                new AgentModelToolCall(null, "search_catalog", "{\"query\":\"hats\"}")
        );
        assertThat(result.finishReason()).isEqualTo("TOOL_CALLS");
    }

    @Test
    void cancellationDuringStreamingStopsBeforeLaterChunksArePublished() {
        ChatModel chatModel = mock(ChatModel.class);
        AtomicReference<SignalType> termination = new AtomicReference<>();
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.concat(
                Flux.just(
                        response("first", List.of(), null, null, null),
                        response("second", List.of(), "STOP", null, null)
                ),
                Flux.never()
        ).doFinally(termination::set));
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofSeconds(1)));
        AtomicInteger cancellationChecks = new AtomicInteger();
        List<String> deltas = new ArrayList<>();

        assertThatThrownBy(() -> gateway.turn(
                request(List.of()),
                deltas::add,
                () -> cancellationChecks.incrementAndGet() >= 3
        )).isInstanceOf(CancellationException.class);

        assertThat(deltas).containsExactly("first");
        assertThat(termination).hasValue(SignalType.CANCEL);
    }

    @Test
    void timesOutAContinuouslyStreamingModelAtTheAbsoluteDeadline() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.interval(Duration.ZERO, Duration.ofMillis(2))
                .map(ignored -> response("", List.of(), null, null, null)));
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofMillis(25)));

        assertSafeUpstreamFailure(() -> gateway.turn(request(List.of()), ignored -> { }, () -> false));
    }

    @Test
    void hidesUnsupportedModelDetailsBehindTheSafeUpstreamError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.error(new UnsupportedOperationException("unsupported-model-secret"))
        );
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofSeconds(1)));

        assertThatExceptionOfType(AgentException.class)
                .isThrownBy(() -> gateway.turn(request(List.of()), ignored -> { }, () -> false))
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UPSTREAM_SERVICE_ERROR);
                    assertThat(exception.getSafeMessage()).isEqualTo("The agent model is temporarily unavailable.");
                    assertThat(exception.getMessage()).doesNotContain("unsupported-model-secret");
                    assertThat(exception.getCause()).isInstanceOf(UnsupportedOperationException.class);
                });
    }

    @Test
    void sendsSchemaOnlyToolDefinitionsAndTurnSpecificOptionsToSpringAi() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("Done", List.of(), "STOP", "configured/model", new DefaultUsage(0, 0))
        ));
        SpringAiAgentModelGateway gateway = new SpringAiAgentModelGateway(chatModel, properties(Duration.ofSeconds(3)));
        AgentModelToolDefinition definition = new AgentModelToolDefinition(
                "search_catalog",
                "Search the catalog",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
        );

        gateway.turn(request(List.of(definition), "search_catalog"), ignored -> { }, () -> false);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("requested/model");
        assertThat(options.getTemperature()).isEqualTo(0.25);
        assertThat(options.getMaxTokens()).isEqualTo(321);
        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(options.getParallelToolCalls()).isTrue();
        assertThat(options.getStreamOptions().includeUsage()).isTrue();
        assertThat(options.getToolChoice())
                .isInstanceOf(ChatCompletionToolChoiceOption.class)
                .extracting(choice -> ((ChatCompletionToolChoiceOption) choice)
                        .asNamedToolChoice()
                        .function()
                        .name())
                .isEqualTo("search_catalog");
        assertThat(options.getToolCallbacks()).singleElement().satisfies(callback -> {
            assertThat(callback.getToolDefinition().name()).isEqualTo("search_catalog");
            assertThat(callback.getToolDefinition().description()).isEqualTo("Search the catalog");
            assertThat(callback.getToolDefinition().inputSchema()).isEqualTo(definition.inputSchemaJson());
            assertThatThrownBy(() -> callback.call("{}"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Meant run coordinator");
        });
    }

    private void assertSafeUpstreamFailure(ThrowingTurn turn) {
        assertThatExceptionOfType(AgentException.class)
                .isThrownBy(turn::execute)
                .satisfies(exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UPSTREAM_SERVICE_ERROR);
                    assertThat(exception.getSafeMessage()).isEqualTo("The agent model is temporarily unavailable.");
                });
    }

    private AgentModelRequest request(List<AgentModelToolDefinition> tools) {
        return request(tools, null);
    }

    private AgentModelRequest request(List<AgentModelToolDefinition> tools, String requiredToolName) {
        return new AgentModelRequest(
                "requested/model",
                List.of(AgentModelMessage.system("system"), AgentModelMessage.user("user")),
                tools,
                0.25,
                321,
                requiredToolName
        );
    }

    private ChatResponse response(
            String text,
            List<AssistantMessage.ToolCall> toolCalls,
            String finishReason,
            String model,
            Usage usage
    ) {
        ChatGenerationMetadata.Builder generationMetadata = ChatGenerationMetadata.builder();
        if (finishReason != null) {
            generationMetadata.finishReason(finishReason);
        }
        AssistantMessage output = AssistantMessage.builder()
                .content(text)
                .toolCalls(toolCalls)
                .build();
        return new ChatResponse(
                List.of(new Generation(output, generationMetadata.build())),
                responseMetadata(model, usage)
        );
    }

    private ChatResponse metadataOnlyResponse(String model, Usage usage) {
        return new ChatResponse(List.of(), responseMetadata(model, usage));
    }

    private ChatResponseMetadata responseMetadata(String model, Usage usage) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
        if (model != null) {
            metadata.model(model);
        }
        if (usage != null) {
            metadata.usage(usage);
        }
        return metadata.build();
    }

    private AssistantMessage.ToolCall toolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private AgentProperties properties(Duration modelTimeout) {
        return new AgentProperties(
                true,
                "configured/model",
                "fallback/model",
                "https://openrouter.example.test/api/v1",
                "api-key",
                "Meant",
                "https://meant.example.test",
                "prompt-v1",
                "tool-v1",
                0.25,
                321,
                8,
                20,
                5,
                4,
                40,
                64_000,
                24_000,
                2,
                Duration.ofSeconds(30),
                modelTimeout,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                Duration.ofMillis(10),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(5)
        );
    }

    @FunctionalInterface
    private interface ThrowingTurn {

        void execute();
    }
}
