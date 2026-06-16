package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenRouterResponseFormat(
        String type,
        @JsonProperty("json_schema")
        OpenRouterJsonSchema jsonSchema
) {
}
