package com.meant.api.common.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRouterJsonSchemaDefinition(
        SchemaType type,
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
                SchemaType.single("object"),
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
                SchemaType.single("array"),
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
                SchemaType.single("string"),
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
                SchemaType.single("number"),
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
                SchemaType.single("string"),
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
                SchemaType.single("boolean"),
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
                SchemaType.union(List.of(valueType, "null")),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public sealed interface SchemaType permits SingleType, UnionType {

        @JsonValue
        JsonNode jsonValue();

        static SchemaType single(String value) {
            return new SingleType(value);
        }

        static SchemaType union(List<String> values) {
            return new UnionType(values);
        }
    }

    public record SingleType(String value) implements SchemaType {

        public SingleType {
            value = requireType(value);
        }

        @Override
        public JsonNode jsonValue() {
            return JsonNodeFactory.instance.textNode(value);
        }
    }

    public record UnionType(List<String> values) implements SchemaType {

        public UnionType {
            values = values == null
                    ? List.of()
                    : values.stream().filter(Objects::nonNull).map(OpenRouterJsonSchemaDefinition::requireType)
                            .distinct().toList();
            if (values.isEmpty()) {
                throw new IllegalArgumentException("schema type union must not be empty");
            }
        }

        @Override
        public JsonNode jsonValue() {
            var array = JsonNodeFactory.instance.arrayNode();
            values.forEach(array::add);
            return array;
        }
    }

    private static String requireType(String value) {
        Objects.requireNonNull(value, "schema type must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("schema type must not be blank");
        }
        return normalized;
    }
}
