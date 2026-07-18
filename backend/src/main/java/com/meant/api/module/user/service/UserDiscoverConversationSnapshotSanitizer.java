package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Keeps the complete typed conversation while reducing rehydratable search result lists to references. */
@Component
@RequiredArgsConstructor
public class UserDiscoverConversationSnapshotSanitizer {

    private static final Set<String> DURABLE_BLOCK_TYPES = Set.of(
            "text",
            "newsletter",
            "products",
            "reviews",
            "code",
            "similar",
            "decision",
            "watch",
            "friendvote",
            "added",
            "saved",
            "orders",
            "prefs",
            "cart",
            "checkout",
            "minicompare",
            "system");
    private static final Set<String> MESSAGE_ROLES = Set.of("you", "ai");
    private static final String UNAVAILABLE_ATTACHMENT_MESSAGE =
            "This attachment is unavailable in conversation history.";

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
            copyText(parsed, snapshot, "focusProductId");
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
        ObjectNode durable = objectMapper.createObjectNode();
        copyText(message, durable, "id");
        durable.put("role", role);
        copyText(message, durable, "text");
        copyBoolean(message, durable, "pending");
        copyText(message, durable, "pendingText");
        copyStringArray(message, durable, "suggestedReplies");
        copyText(message, durable, "query");
        copyObject(message, durable, "productContext");
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

    private ArrayNode durableBlocks(JsonNode blocks) {
        ArrayNode durable = objectMapper.createArrayNode();
        if (blocks == null) {
            return durable;
        }
        if (!blocks.isArray()) {
            durable.add(unavailableAttachmentMarker());
            return durable;
        }
        for (JsonNode block : blocks) {
            durable.add(durableBlock(block));
        }
        return durable;
    }

    private ObjectNode durableBlock(JsonNode block) {
        if (!block.isObject()) {
            return unavailableAttachmentMarker();
        }
        String type = block.path("type").asText();
        if (!DURABLE_BLOCK_TYPES.contains(type)) {
            return unavailableAttachmentMarker();
        }
        if ("products".equals(type) && hasProductResultReference(block)) {
            ObjectNode durable = objectMapper.createObjectNode();
            durable.put("type", type);
            copyText(block, durable, "productResultSetId");
            copyText(block, durable, "query");
            return durable;
        }
        return (ObjectNode) block.deepCopy();
    }

    private boolean hasProductResultReference(JsonNode block) {
        JsonNode resultSetId = block.get("productResultSetId");
        JsonNode query = block.get("query");
        if (resultSetId == null || !resultSetId.isTextual()
                || query == null || !query.isTextual() || query.asText().isBlank()) {
            return false;
        }
        try {
            String value = resultSetId.asText().trim();
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ObjectNode unavailableAttachmentMarker() {
        ObjectNode marker = objectMapper.createObjectNode();
        marker.put("type", "system");
        marker.put("text", UNAVAILABLE_ATTACHMENT_MESSAGE);
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

    private void copyObject(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && value.isObject()) {
            target.set(field, value.deepCopy());
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
