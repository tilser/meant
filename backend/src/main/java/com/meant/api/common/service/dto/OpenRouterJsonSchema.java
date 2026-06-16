package com.meant.api.common.service.dto;

public record OpenRouterJsonSchema(
        String name,
        boolean strict,
        OpenRouterJsonSchemaDefinition schema
) {
}
