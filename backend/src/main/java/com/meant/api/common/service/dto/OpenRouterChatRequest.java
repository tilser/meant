package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenRouterChatRequest(
        String model,
        List<OpenRouterChatMessage> messages,
        Double temperature,
        @JsonProperty("response_format")
        OpenRouterResponseFormat responseFormat
) {
}
