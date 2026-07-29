package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterChatRequest(
        String model,
        List<OpenRouterChatMessage> messages,
        Double temperature,
        @JsonProperty("response_format")
        OpenRouterResponseFormat responseFormat,
        List<OpenRouterPlugin> plugins,
        @JsonProperty("max_tokens")
        Integer maximumTokens,
        Boolean stream
) {

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat
    ) {
        this(model, messages, temperature, responseFormat, null, null, false);
    }

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat,
            List<OpenRouterPlugin> plugins
    ) {
        this(model, messages, temperature, responseFormat, plugins, null, false);
    }

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat,
            List<OpenRouterPlugin> plugins,
            Integer maximumTokens
    ) {
        this(model, messages, temperature, responseFormat, plugins, maximumTokens, false);
    }
}
