package com.meant.api.common.service;

import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * Keeps the agent gateway provider-neutral while adapting its Spring AI tool options
 * to the concrete options type required by {@code OpenAiChatModel}.
 */
public final class OpenAiToolCallingChatModelAdapter implements ChatModel {

    private final ChatModel delegate;
    private final OpenAiChatOptions defaultOptions;

    public OpenAiToolCallingChatModelAdapter(ChatModel delegate, OpenAiChatOptions defaultOptions) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate.call(providerPrompt(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate.stream(providerPrompt(prompt));
    }

    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    private Prompt providerPrompt(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options == null || options instanceof OpenAiChatOptions) {
            return prompt;
        }
        if (!(options instanceof ToolCallingChatOptions toolOptions)) {
            throw new IllegalArgumentException(
                    "OpenAI agent model requires tool-calling chat options, received "
                            + options.getClass().getName()
            );
        }

        OpenAiChatOptions.Builder builder = defaultOptions.mutate();
        if (toolOptions.getModel() != null) {
            builder.model(toolOptions.getModel());
        }
        if (toolOptions.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(toolOptions.getFrequencyPenalty());
        }
        if (toolOptions.getMaxTokens() != null) {
            builder.maxTokens(toolOptions.getMaxTokens());
        }
        if (toolOptions.getPresencePenalty() != null) {
            builder.presencePenalty(toolOptions.getPresencePenalty());
        }
        if (toolOptions.getStopSequences() != null) {
            builder.stop(toolOptions.getStopSequences());
        }
        if (toolOptions.getTemperature() != null) {
            builder.temperature(toolOptions.getTemperature());
        }
        if (toolOptions.getTopP() != null) {
            builder.topP(toolOptions.getTopP());
        }
        builder.toolCallbacks(toolOptions.getToolCallbacks());
        builder.toolContext(toolOptions.getToolContext());

        return new Prompt(prompt.getInstructions(), builder.build());
    }
}
