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
        Boolean stream
) {

    public OpenRouterChatRequest(
            String model,
            List<OpenRouterChatMessage> messages,
            Double temperature,
            OpenRouterResponseFormat responseFormat
    ) {
        this(model, messages, temperature, responseFormat, false);
    }
}
