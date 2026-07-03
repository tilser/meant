package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterPlugin(
        String id,
        @JsonProperty("max_results")
        Integer maxResults
) {
}
