package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterJsonSchemaDefinition(
        Object type,
        Boolean additionalProperties,
        List<String> required,
        Map<String, OpenRouterJsonSchemaDefinition> properties,
        OpenRouterJsonSchemaDefinition items,
        @JsonProperty("minItems")
        Integer minItems,
        @JsonProperty("maxItems")
        Integer maxItems,
        @JsonProperty("enum")
        List<String> enumValues
) {

    public static OpenRouterJsonSchemaDefinition object(
            List<String> required,
            Map<String, OpenRouterJsonSchemaDefinition> properties
    ) {
        return new OpenRouterJsonSchemaDefinition(
                "object",
                false,
                required,
                properties,
                null,
                null,
                null,
                null
        );
    }

    public static OpenRouterJsonSchemaDefinition array(OpenRouterJsonSchemaDefinition items) {
        return array(items, null, null);
    }

    public static OpenRouterJsonSchemaDefinition array(
            OpenRouterJsonSchemaDefinition items,
            Integer minItems,
            Integer maxItems
    ) {
        return new OpenRouterJsonSchemaDefinition(
                "array",
                null,
                null,
                null,
                items,
                minItems,
                maxItems,
                null
        );
    }

    public static OpenRouterJsonSchemaDefinition string() {
        return new OpenRouterJsonSchemaDefinition(
                "string",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static OpenRouterJsonSchemaDefinition nullableString() {
        return nullableType("string");
    }

    public static OpenRouterJsonSchemaDefinition number() {
        return new OpenRouterJsonSchemaDefinition(
                "number",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static OpenRouterJsonSchemaDefinition nullableNumber() {
        return nullableType("number");
    }

    public static OpenRouterJsonSchemaDefinition stringEnum(List<String> values) {
        return new OpenRouterJsonSchemaDefinition(
                "string",
                null,
                null,
                null,
                null,
                null,
                null,
                values
        );
    }

    public static OpenRouterJsonSchemaDefinition bool() {
        return new OpenRouterJsonSchemaDefinition(
                "boolean",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static OpenRouterJsonSchemaDefinition nullableBool() {
        return nullableType("boolean");
    }

    private static OpenRouterJsonSchemaDefinition nullableType(String valueType) {
        return new OpenRouterJsonSchemaDefinition(
                List.of(valueType, "null"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
