package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenRouterChatResponse(
        List<Choice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            OpenRouterChatMessage message
    ) {
    }
}
