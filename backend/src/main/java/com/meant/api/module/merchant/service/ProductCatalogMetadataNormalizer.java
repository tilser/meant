package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogMetadataNormalizer {

    private static final int MAX_METADATA_DEPTH = 64;

    List<ProductCatalogAttribute> attributes(Object value) {
        if (value == null) {
            return List.of();
        }
        List<ProductCatalogAttribute> attributes = new ArrayList<>();
        List<AttributeNode> stack = new ArrayList<>();
        stack.add(new AttributeNode("metadata", value, 0));
        while (!stack.isEmpty()) {
            AttributeNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            addAttributeValue(attributes, stack, node.name(), node.value(), node.depth());
        }
        return attributes;
    }

    private void addAttributeValue(
            List<ProductCatalogAttribute> attributes,
            List<AttributeNode> stack,
            String key,
            Object value,
            int depth
    ) {
        if (value instanceof Map<?, ?> map) {
            Object namedValue = firstMapValue(map, "value", "values", "text", "description");
            String namedKey = firstPresent(firstStringValue(map, "name", "key", "label", "title"), key);
            if (namedValue != null) {
                String stringValue = String.join(", ", stringValues(namedValue));
                if (!stringValue.isBlank()) {
                    attributes.add(new ProductCatalogAttribute(namedKey, stringValue));
                }
                return;
            }
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            for (int index = entries.size() - 1; index >= 0; index--) {
                Map.Entry<?, ?> entry = entries.get(index);
                String nestedName = scalarString(entry.getKey());
                if (nestedName != null) {
                    String attributeName = "metadata".equals(key) ? nestedName : key + " " + nestedName;
                    stack.add(new AttributeNode(attributeName, entry.getValue(), depth + 1));
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            List<String> values = stringValues(collection);
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

    List<String> stringValues(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        List<ValueNode> stack = new ArrayList<>();
        stack.add(new ValueNode(value, 0));
        while (!stack.isEmpty()) {
            ValueNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            if (node.value() instanceof Collection<?> collection) {
                List<?> items = new ArrayList<>(collection);
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            if (node.value() instanceof Map<?, ?> map) {
                Object namedValues = firstMapValue(map, "values", "value", "name", "label", "title", "text");
                if (namedValues != null) {
                    stack.add(new ValueNode(namedValues, node.depth() + 1));
                    continue;
                }
                List<?> mapValues = new ArrayList<>(map.values());
                for (int index = mapValues.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(mapValues.get(index), node.depth() + 1));
                }
                continue;
            }
            String scalar = scalarString(node.value());
            if (scalar != null) {
                Stream.of(scalar.split("\\s*[,;/|]\\s*"))
                        .map(this::blankToNull)
                        .filter(Objects::nonNull)
                        .forEach(values::add);
            }
        }
        return values;
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String firstStringValue(Map<?, ?> map, String... keys) {
        Object value = firstMapValue(map, keys);
        return scalarString(value);
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

    private String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return blankToNull(string);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return blankToNull(value.toString());
        }
        return null;
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

    private record AttributeNode(String name, Object value, int depth) {
    }

    private record ValueNode(Object value, int depth) {
    }
}
