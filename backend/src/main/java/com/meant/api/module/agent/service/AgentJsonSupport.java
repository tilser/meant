package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AgentJsonSupport {

    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public <T> T readArguments(String json, Class<T> type) {
        requireBounded(json);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw invalidArguments("Tool arguments must be a JSON object.");
            }
            rejectUserIdentifiers(root);
            return objectMapper.treeToValue(root, type);
        } catch (AgentException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw invalidArguments("Tool arguments did not match the required schema.");
        }
    }

    public String validateArguments(String json) {
        requireBounded(json);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw invalidArguments("Tool arguments must be a JSON object.");
            }
            rejectUserIdentifiers(root);
            return objectMapper.writeValueAsString(canonicalValue(root));
        } catch (AgentException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw invalidArguments("Tool arguments did not match the required schema.");
        }
    }

    public String write(Object value) {
        try {
            return truncate(objectMapper.writeValueAsString(value));
        } catch (JacksonException exception) {
            throw new AgentException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "Agent data could not be serialized.",
                    exception
            );
        }
    }

    /** Serializes server-owned durable UI state without replacing its contract with a preview. */
    public String writeArtifact(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new AgentException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiErrorCode.INTERNAL_ERROR,
                    "Agent artifact could not be serialized.",
                    exception
            );
        }
    }

    public String bounded(String value) {
        if (value == null) {
            return null;
        }
        return truncate(value);
    }

    public String canonicalizeOrOriginal(String value) {
        try {
            return validateArguments(value);
        } catch (RuntimeException exception) {
            return value == null ? "{}" : value.trim();
        }
    }

    private void requireBounded(String value) {
        if (value == null || value.isBlank() || value.length() > properties.maximumResultCharacters()) {
            throw invalidArguments("Tool arguments were empty or too large.");
        }
    }

    private String truncate(String value) {
        int maximum = properties.maximumResultCharacters();
        if (value.length() <= maximum) {
            return value;
        }
        int previewLength = Math.max(0, maximum - 128);
        try {
            return objectMapper.writeValueAsString(new TruncatedPayload(
                    true,
                    value.length(),
                    value.substring(0, Math.min(previewLength, value.length()))
            ));
        } catch (JacksonException exception) {
            return "{\"truncated\":true}";
        }
    }

    private void rejectUserIdentifiers(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalized = field.getKey().replace("_", "").replace("-", "").toLowerCase();
                if (normalized.equals("userid") || normalized.equals("ownerid")) {
                    throw invalidArguments("User identifiers are server controlled and cannot be supplied to tools.");
                }
                rejectUserIdentifiers(field.getValue());
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(this::rejectUserIdentifiers);
        }
    }

    private Object canonicalValue(JsonNode node) throws JacksonException {
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sorted.put(field.getKey(), canonicalValue(field.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode element : node) {
                values.add(canonicalValue(element));
            }
            return values;
        }
        return objectMapper.treeToValue(node, Object.class);
    }

    private AgentException invalidArguments(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    private record TruncatedPayload(boolean truncated, int originalCharacters, String preview) {
    }
}
