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
        Boolean stream
) {

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat
    ) {
        this(model, messages, temperature, responseFormat, null, false);
    }

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat,
            Boolean stream
    ) {
        this(model, messages, temperature, responseFormat, null, stream);
    }

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat,
            List<OpenRouterPlugin> plugins
    ) {
        this(model, messages, temperature, responseFormat, plugins, false);
    }
}
