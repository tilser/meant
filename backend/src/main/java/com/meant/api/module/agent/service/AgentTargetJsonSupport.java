package com.meant.api.module.agent.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AgentTargetJsonSupport {

    private AgentTargetJsonSupport() {
    }

    static Set<String> nestedTextValues(
            ObjectMapper objectMapper,
            String json,
            String field
    ) {
        Set<String> values = new HashSet<>();
        collectField(objectMapper.readTree(json), field, values);
        return values;
    }

    static Set<String> stringValues(ObjectMapper objectMapper, String json) {
        JsonNode node = objectMapper.readTree(json);
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
        });
        return Set.copyOf(values);
    }

    static Set<String> arrayValues(JsonNode arguments, String field) {
        JsonNode values = arguments == null ? null : arguments.get(field);
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        });
        return Set.copyOf(result);
    }

    static Set<String> arrayField(JsonNode arguments, String arrayField, String itemField) {
        JsonNode values = arguments == null ? null : arguments.get(arrayField);
        if (values == null || !values.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> {
            String item = text(value, itemField);
            if (item != null && !item.isBlank()) {
                result.add(item);
            }
        });
        return Set.copyOf(result);
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static void collectField(JsonNode node, String field, Set<String> values) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText());
            }
            node.properties().forEach(entry -> collectField(entry.getValue(), field, values));
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectField(value, field, values));
        }
    }
}
