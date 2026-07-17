package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Enforces the session-only catalog retention boundary on every durable Discover snapshot. */
@Component
@RequiredArgsConstructor
public class UserDiscoverConversationSnapshotSanitizer {

    private static final Set<String> DURABLE_BLOCK_TYPES = Set.of(
            "text", "newsletter", "prefs", "system");
    private static final Set<String> MESSAGE_ROLES = Set.of("you", "ai");
    private static final String SESSION_ONLY_MESSAGE =
            "Product results are available only in the active session. Search again to refresh them.";

    private final ObjectMapper objectMapper;

    public String sanitize(String threadJson) {
        try {
            JsonNode parsed = objectMapper.readTree(threadJson);
            if (parsed == null || !parsed.isObject()) {
                throw new UserException("Discover conversation snapshot must be a JSON object");
            }
            ObjectNode snapshot = objectMapper.createObjectNode();
            copyText(parsed, snapshot, "id");
            copyText(parsed, snapshot, "title");
            copyText(parsed, snapshot, "qualificationId");
            copyBoolean(parsed, snapshot, "named");
            copyBoolean(parsed, snapshot, "archived");
            copyNumber(parsed, snapshot, "createdAt");
            copyNumber(parsed, snapshot, "updatedAt");
            snapshot.set("messages", sanitizeMessages(parsed.get("messages")));
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new UserException("Discover conversation snapshot was not valid JSON", exception);
        }
    }

    private ArrayNode sanitizeMessages(JsonNode messages) {
        ArrayNode durableMessages = objectMapper.createArrayNode();
        if (messages == null) {
            return durableMessages;
        }
        if (!messages.isArray()) {
            throw new UserException("Discover conversation messages must be an array");
        }
        for (JsonNode message : messages) {
            if (!message.isObject()) {
                throw new UserException("Discover conversation message must be a JSON object");
            }
            durableMessages.add(sanitizeMessage(message));
        }
        return durableMessages;
    }

    private ObjectNode sanitizeMessage(JsonNode message) {
        String role = requiredRole(message);
        boolean removedSessionData = message.has("productContext")
                || message.path("sessionOnly").asBoolean(false)
                || containsSessionBlock(message.get("blocks"));
        if ("ai".equals(role) && removedSessionData) {
            ObjectNode markerMessage = objectMapper.createObjectNode();
            copyText(message, markerMessage, "id");
            markerMessage.put("role", "ai");
            ArrayNode blocks = objectMapper.createArrayNode();
            blocks.add(sessionOnlyMarker());
            markerMessage.set("blocks", blocks);
            return markerMessage;
        }

        ObjectNode durable = objectMapper.createObjectNode();
        copyText(message, durable, "id");
        durable.put("role", role);
        copyText(message, durable, "text");
        copyBoolean(message, durable, "pending");
        copyText(message, durable, "pendingText");
        copyStringArray(message, durable, "suggestedReplies");
        copyText(message, durable, "query");
        ArrayNode blocks = durableBlocks(message.get("blocks"));
        if (!blocks.isEmpty()) {
            durable.set("blocks", blocks);
        }
        return durable;
    }

    private String requiredRole(JsonNode message) {
        JsonNode role = message.get("role");
        if (role == null || !role.isTextual() || !MESSAGE_ROLES.contains(role.asText())) {
            throw new UserException("Discover conversation message role was invalid");
        }
        return role.asText();
    }

    private boolean containsSessionBlock(JsonNode blocks) {
        if (blocks == null) {
            return false;
        }
        if (!blocks.isArray()) {
            return true;
        }
        for (JsonNode block : blocks) {
            if (!block.isObject() || !DURABLE_BLOCK_TYPES.contains(block.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private ArrayNode durableBlocks(JsonNode blocks) {
        ArrayNode durable = objectMapper.createArrayNode();
        if (blocks == null || !blocks.isArray()) {
            return durable;
        }
        for (JsonNode block : blocks) {
            ObjectNode sanitized = durableBlock(block);
            if (sanitized != null) {
                durable.add(sanitized);
            }
        }
        return durable;
    }

    private ObjectNode durableBlock(JsonNode block) {
        if (!block.isObject()) {
            return null;
        }
        String type = block.path("type").asText();
        if (!DURABLE_BLOCK_TYPES.contains(type)) {
            return null;
        }
        ObjectNode durable = objectMapper.createObjectNode();
        durable.put("type", type);
        switch (type) {
            case "text", "system" -> copyText(block, durable, "text");
            case "prefs" -> durable.set("preferences", durablePreferences(block.get("preferences")));
            case "newsletter" -> {
                // The block is intentionally fieldless.
            }
            default -> throw new IllegalStateException("Unexpected durable Discover block type");
        }
        return durable;
    }

    private ArrayNode durablePreferences(JsonNode preferences) {
        ArrayNode durable = objectMapper.createArrayNode();
        if (preferences == null || !preferences.isArray()) {
            return durable;
        }
        for (JsonNode preference : preferences) {
            if (!preference.isObject()) {
                continue;
            }
            ObjectNode value = objectMapper.createObjectNode();
            copyText(preference, value, "id");
            copyText(preference, value, "label");
            copyText(preference, value, "desc");
            copyText(preference, value, "category");
            copyText(preference, value, "polarity");
            copyNumber(preference, value, "displayOrder");
            durable.add(value);
        }
        return durable;
    }

    private ObjectNode sessionOnlyMarker() {
        ObjectNode marker = objectMapper.createObjectNode();
        marker.put("type", "system");
        marker.put("text", SESSION_ONLY_MESSAGE);
        return marker;
    }

    private void copyText(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isTextual()) {
            target.put(field, value.asText());
        }
    }

    private void copyBoolean(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isBoolean()) {
            target.put(field, value.asBoolean());
        }
    }

    private void copyNumber(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isNumber()) {
            target.set(field, value.deepCopy());
        }
    }

    private void copyStringArray(JsonNode source, ObjectNode target, String field) {
        JsonNode values = source.get(field);
        if (values == null || !values.isArray()) {
            return;
        }
        ArrayNode strings = objectMapper.createArrayNode();
        for (JsonNode value : values) {
            if (value.isTextual()) {
                strings.add(value.asText());
            }
        }
        target.set(field, strings);
    }
}
