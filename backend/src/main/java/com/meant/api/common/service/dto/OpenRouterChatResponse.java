package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenRouterChatResponse(
        String model,
        List<Choice> choices,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            OpenRouterChatMessage message,
            @JsonProperty("finish_reason")
            String finishReason,
            @JsonProperty("native_finish_reason")
            String nativeFinishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens")
            Long promptTokens,
            @JsonProperty("completion_tokens")
            Long completionTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
