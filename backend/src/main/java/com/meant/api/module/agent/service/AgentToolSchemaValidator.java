package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AgentToolSchemaValidator {

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();

    public void validate(String schemaJson, String argumentsJson) {
        try {
            JsonNode schema = schemas.computeIfAbsent(schemaJson, this::parseSchema);
            JsonNode arguments = objectMapper.readTree(argumentsJson);
            validateNode(schema, arguments, "$", true);
        } catch (AgentException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw invalid("Tool arguments did not match the published schema.");
        }
    }

    private JsonNode parseSchema(String value) {
        try {
            JsonNode schema = objectMapper.readTree(value);
            if (schema == null || !schema.isObject()) {
                throw new IllegalStateException("Agent tool schema must be a JSON object");
            }
            return schema;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Agent tool schema is invalid", exception);
        }
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, boolean report) {
        JsonNode anyOf = schema.get("anyOf");
        if (anyOf != null && anyOf.isArray()) {
            boolean matched = false;
            for (JsonNode candidate : anyOf) {
                if (valid(candidate, value, path)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                fail(report, path + " did not match any allowed argument shape.");
            }
        }

        String type = schema.get("type") == null ? null : schema.get("type").asText();
        if (type != null && !matchesType(type, value)) {
            fail(report, path + " must be a " + type + ".");
            return;
        }
        validateEnum(schema, value, path, report);
        if (value != null && value.isObject()) {
            validateObject(schema, value, path, report);
        } else if (value != null && value.isArray()) {
            validateArray(schema, value, path, report);
        } else if (value != null && value.isTextual()) {
            validateString(schema, value.asText(), path, report);
        } else if (value != null && value.isNumber()) {
            validateNumber(schema, value, path, report);
        }
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, boolean report) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode name : required) {
                if (!value.has(name.asText())) {
                    fail(report, path + "." + name.asText() + " is required.");
                }
            }
        }
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode propertySchema = properties.get(field.getKey());
                if (propertySchema == null) {
                    JsonNode additional = schema.get("additionalProperties");
                    if (additional != null && additional.isBoolean() && !additional.asBoolean()) {
                        fail(report, path + "." + field.getKey() + " is not allowed.");
                    }
                    continue;
                }
                validateNode(propertySchema, field.getValue(), path + "." + field.getKey(), report);
            }
        }
    }

    private void validateArray(JsonNode schema, JsonNode value, String path, boolean report) {
        int size = value.size();
        JsonNode minimum = schema.get("minItems");
        JsonNode maximum = schema.get("maxItems");
        if (minimum != null && size < minimum.asInt()) {
            fail(report, path + " has too few items.");
        }
        if (maximum != null && size > maximum.asInt()) {
            fail(report, path + " has too many items.");
        }
        JsonNode items = schema.get("items");
        if (items != null) {
            for (int index = 0; index < size; index++) {
                validateNode(items, value.get(index), path + "[" + index + "]", report);
            }
        }
        JsonNode unique = schema.get("uniqueItems");
        if (unique != null && unique.asBoolean()) {
            Set<JsonNode> observed = new HashSet<>();
            for (JsonNode item : value) {
                if (!observed.add(item)) {
                    fail(report, path + " must not contain duplicate items.");
                }
            }
        }
    }

    private void validateString(JsonNode schema, String value, String path, boolean report) {
        JsonNode minimum = schema.get("minLength");
        JsonNode maximum = schema.get("maxLength");
        if (minimum != null && value.length() < minimum.asInt()) {
            fail(report, path + " is too short.");
        }
        if (maximum != null && value.length() > maximum.asInt()) {
            fail(report, path + " is too long.");
        }
        JsonNode format = schema.get("format");
        if (format == null) {
            return;
        }
        boolean valid = switch (format.asText()) {
            case "uuid" -> validUuid(value);
            case "date" -> validDate(value);
            case "email" -> validEmail(value);
            default -> true;
        };
        if (!valid) {
            fail(report, path + " has an invalid " + format.asText() + " format.");
        }
    }

    private void validateNumber(JsonNode schema, JsonNode value, String path, boolean report) {
        JsonNode minimum = schema.get("minimum");
        JsonNode maximum = schema.get("maximum");
        if (minimum != null && value.asDouble() < minimum.asDouble()) {
            fail(report, path + " is below the minimum.");
        }
        if (maximum != null && value.asDouble() > maximum.asDouble()) {
            fail(report, path + " is above the maximum.");
        }
    }

    private void validateEnum(JsonNode schema, JsonNode value, String path, boolean report) {
        JsonNode allowed = schema.get("enum");
        if (allowed == null || !allowed.isArray()) {
            return;
        }
        for (JsonNode candidate : allowed) {
            if (candidate.equals(value)) {
                return;
            }
        }
        fail(report, path + " is not an allowed value.");
    }

    private boolean matchesType(String type, JsonNode value) {
        return value != null && switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> false;
        };
    }

    private boolean valid(JsonNode schema, JsonNode value, String path) {
        try {
            validateNode(schema, value, path, false);
            return true;
        } catch (SchemaMismatch ignored) {
            return false;
        }
    }

    private void fail(boolean report, String message) {
        if (report) {
            throw invalid(message);
        }
        throw new SchemaMismatch();
    }

    private boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean validDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean validEmail(String value) {
        int separator = value.indexOf('@');
        return separator > 0 && separator < value.length() - 1 && value.indexOf('\n') < 0;
    }

    private AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    private static final class SchemaMismatch extends RuntimeException {
    }
}
