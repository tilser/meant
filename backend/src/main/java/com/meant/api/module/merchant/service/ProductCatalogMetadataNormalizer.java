package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class ProductCatalogMetadataNormalizer {

    private static final int MAX_METADATA_DEPTH = 64;

    List<ProductCatalogAttribute> attributes(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        List<ProductCatalogAttribute> attributes = new ArrayList<>();
        List<AttributeNode> stack = new ArrayList<>();
        stack.add(new AttributeNode("metadata", value, 0));
        while (!stack.isEmpty()) {
            AttributeNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null
                    || node.value().isNull() || node.value().isMissingNode()) {
                continue;
            }
            addJsonAttributeValue(attributes, stack, node.name(), node.value(), node.depth());
        }
        return attributes;
    }

    List<ProductCatalogAttribute> attributes(List<String> values) {
        List<String> normalized = stringValues(values);
        return normalized.isEmpty()
                ? List.of()
                : List.of(new ProductCatalogAttribute("technical specification", String.join(", ", normalized)));
    }

    private void addJsonAttributeValue(
            List<ProductCatalogAttribute> attributes,
            List<AttributeNode> stack,
            String key,
            JsonNode value,
            int depth
    ) {
        if (value.isNull() || value.isMissingNode()) {
            return;
        }
        if (value.isObject()) {
            JsonNode namedValue = firstJsonValue(value, "value", "values", "text", "description");
            String namedKey = firstPresent(firstJsonString(value, "name", "key", "label", "title"), key);
            if (namedValue != null) {
                String stringValue = String.join(", ", stringValues(namedValue));
                if (!stringValue.isBlank()) {
                    attributes.add(new ProductCatalogAttribute(namedKey, stringValue));
                }
                return;
            }
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(value.properties());
            for (int index = entries.size() - 1; index >= 0; index--) {
                Map.Entry<String, JsonNode> entry = entries.get(index);
                String attributeName = "metadata".equals(key) ? entry.getKey() : key + " " + entry.getKey();
                stack.add(new AttributeNode(attributeName, entry.getValue(), depth + 1));
            }
            return;
        }
        if (value.isArray()) {
            List<String> values = stringValues(value);
            if (!values.isEmpty()) {
                attributes.add(new ProductCatalogAttribute(key, String.join(", ", values)));
            }
            return;
        }
        String scalar = scalarString(value);
        if (scalar != null) {
            attributes.add(new ProductCatalogAttribute(key, scalar));
        }
    }

    List<String> stringValues(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        List<ValueNode> stack = new ArrayList<>();
        stack.add(new ValueNode(value, 0));
        while (!stack.isEmpty()) {
            ValueNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null
                    || node.value().isNull() || node.value().isMissingNode()) {
                continue;
            }
            JsonNode json = node.value();
            if (json.isArray()) {
                List<JsonNode> items = new ArrayList<>(json.values());
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            if (json.isObject()) {
                JsonNode namedValues = firstJsonValue(
                        json, "values", "value", "name", "label", "title", "text");
                if (namedValues != null) {
                    stack.add(new ValueNode(namedValues, node.depth() + 1));
                    continue;
                }
                List<JsonNode> mapValues = new ArrayList<>(json.values());
                for (int index = mapValues.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(mapValues.get(index), node.depth() + 1));
                }
                continue;
            }
            String scalar = scalarString(json);
            if (scalar != null) {
                values.add(scalar);
            }
        }
        return values;
    }

    List<String> stringValues(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return distinctStrings(source.stream().flatMap(this::splitString));
    }

    List<String> stringValues(String source) {
        return source == null ? List.of() : distinctStrings(splitString(source));
    }

    private Stream<String> splitString(String value) {
        return value == null ? Stream.empty() : Stream.of(value.split("\\s*[,;/|]\\s*"));
    }

    private JsonNode firstJsonValue(JsonNode value, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, JsonNode> entry : value.properties()) {
                if (key.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null
                        && !entry.getValue().isNull()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String firstJsonString(JsonNode value, String... keys) {
        return scalarString(firstJsonValue(value, keys));
    }

    List<String> distinctStrings(Stream<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        return values
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .filter(value -> seen.add(value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    boolean containsAny(String value, List<String> fragments) {
        String normalized = normalizedValue(value);
        return fragments.stream().anyMatch(normalized::contains);
    }

    private String scalarString(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isValueNode() ? blankToNull(value.asText()) : null;
    }

    String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    String normalizedValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private record AttributeNode(String name, JsonNode value, int depth) {
    }

    private record ValueNode(JsonNode value, int depth) {
    }
}
