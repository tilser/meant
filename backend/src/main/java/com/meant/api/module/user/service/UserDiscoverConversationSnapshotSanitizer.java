package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.dto.SanitizedUserDiscoverConversationSnapshot;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Keeps typed conversation history while reducing rehydratable product facts to durable references. */
@Component
@RequiredArgsConstructor
public class UserDiscoverConversationSnapshotSanitizer {

    static final String SIMILAR_AUTO_TITLE_SOURCE = "similar-product-search";
    static final String SIMILAR_THREAD_TITLE = "Similar products";

    private static final Set<String> DURABLE_BLOCK_TYPES = Set.of(
            "text",
            "newsletter",
            "products",
            "reviews",
            "code",
            "similar",
            "similar-reference",
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
    private static final String SIMILAR_REQUEST_TEXT = "Show me similar products.";
    private static final String SIMILAR_PENDING_TEXT = "Meant is finding similar products.";
    private static final int MAX_CANONICAL_PRODUCT_KEY_LENGTH = 200;
    private static final int MAX_ORIGINATING_QUERY_LENGTH = 500;
    private static final int MAX_SIMILAR_RESULTS = 20;

    private final ObjectMapper objectMapper;

    public String sanitize(String threadJson) {
        return sanitized(threadJson).threadJson();
    }

    public SanitizedUserDiscoverConversationSnapshot sanitize(String title, String threadJson) {
        SanitizedSnapshot snapshot = sanitized(threadJson);
        return new SanitizedUserDiscoverConversationSnapshot(
                snapshot.similarityGeneratedTitle() ? SIMILAR_THREAD_TITLE : title,
                snapshot.threadJson()
        );
    }

    private SanitizedSnapshot sanitized(String threadJson) {
        try {
            JsonNode parsed = objectMapper.readTree(threadJson);
            if (parsed == null || !parsed.isObject()) {
                throw new UserException("Discover conversation snapshot must be a JSON object");
            }
            ObjectNode snapshot = objectMapper.createObjectNode();
            copyText(parsed, snapshot, "id");
            copyText(parsed, snapshot, "title");
            copyUuid(parsed, snapshot, "qualificationId");
            copyText(parsed, snapshot, "focusProductId");
            copyBoolean(parsed, snapshot, "named");
            copyBoolean(parsed, snapshot, "archived");
            copyNumber(parsed, snapshot, "createdAt");
            copyNumber(parsed, snapshot, "updatedAt");

            SanitizedMessages messages = sanitizeMessages(parsed.get("messages"));
            snapshot.set("messages", messages.messages());
            boolean similarityAutoTitle = SIMILAR_AUTO_TITLE_SOURCE.equals(text(parsed.get("autoTitleSource")));
            if (similarityAutoTitle) {
                snapshot.put("autoTitleSource", SIMILAR_AUTO_TITLE_SOURCE);
            }
            boolean similarityGeneratedTitle = similarityAutoTitle
                    || (messages.containsSimilarity()
                            && !messages.containsNonSimilarity()
                            && !parsed.path("named").asBoolean(false));
            if (similarityGeneratedTitle) {
                snapshot.put("title", SIMILAR_THREAD_TITLE);
            }
            return new SanitizedSnapshot(
                    objectMapper.writeValueAsString(snapshot),
                    similarityGeneratedTitle
            );
        } catch (JacksonException exception) {
            throw new UserException("Discover conversation snapshot was not valid JSON", exception);
        }
    }

    private SanitizedMessages sanitizeMessages(JsonNode messages) {
        ArrayNode durableMessages = objectMapper.createArrayNode();
        if (messages == null) {
            return new SanitizedMessages(durableMessages, false, false);
        }
        if (!messages.isArray()) {
            throw new UserException("Discover conversation messages must be an array");
        }
        boolean containsSimilarity = false;
        boolean containsNonSimilarity = false;
        for (JsonNode message : messages) {
            if (!message.isObject()) {
                throw new UserException("Discover conversation message must be a JSON object");
            }
            String similarRole = similarRole(message);
            if (similarRole == null) {
                durableMessages.add(sanitizeMessage(message));
                containsNonSimilarity = true;
            } else {
                durableMessages.add(sanitizeSimilarMessage(message, similarRole));
                containsSimilarity = true;
            }
        }
        return new SanitizedMessages(durableMessages, containsSimilarity, containsNonSimilarity);
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

    private ObjectNode sanitizeSimilarMessage(JsonNode message, String similarRole) {
        ObjectNode durable = objectMapper.createObjectNode();
        copyText(message, durable, "id");
        durable.put("role", requiredRole(message));
        durable.put("similarMessageRole", similarRole);

        String status = similarStatus(message.get("similarSearchStatus"));
        boolean pending = message.path("pending").asBoolean(false);
        boolean containsSimilarBlock = containsSimilarBlock(message);
        ObjectNode reference = firstValidSimilarReference(message);
        if ("response".equals(similarRole) && !pending) {
            if (reference == null && (containsSimilarBlock || "success".equals(status))) {
                status = "error";
            } else if (reference != null && status == null) {
                status = "success";
            }
        }
        if (status != null) {
            durable.put("similarSearchStatus", status);
        }

        String anchorCanonicalProductKey = canonicalProductKey(message.get("similarAnchorCanonicalProductKey"));
        if (anchorCanonicalProductKey != null) {
            durable.put("similarAnchorCanonicalProductKey", anchorCanonicalProductKey);
        }
        String query = originatingQuery(message.get("query"));
        if (query != null) {
            durable.put("query", query);
        }
        copyUuid(message, durable, "qualificationId");

        durable.put("text", "request".equals(similarRole)
                ? SIMILAR_REQUEST_TEXT
                : similarResponseText(status));
        if (pending) {
            durable.put("pending", true);
            durable.put("pendingText", SIMILAR_PENDING_TEXT);
            durable.put("pendingOperation", SIMILAR_AUTO_TITLE_SOURCE);
        }

        ArrayNode blocks = objectMapper.createArrayNode();
        if ("response".equals(similarRole) && !pending) {
            ObjectNode textBlock = objectMapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", similarResponseText(status));
            blocks.add(textBlock);
        }
        if (reference != null) {
            blocks.add(reference);
        }
        if (message.has("blocks") || !blocks.isEmpty()) {
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
            durable.set("products", objectMapper.createArrayNode());
            copyText(block, durable, "productResultSetId");
            copyText(block, durable, "query");
            copyUuid(block, durable, "qualificationId");
            return durable;
        }
        if ("similar".equals(type) || "similar-reference".equals(type)) {
            ObjectNode reference = similarReference(block, null);
            return reference == null ? unavailableAttachmentMarker() : reference;
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

    private ObjectNode firstValidSimilarReference(JsonNode message) {
        JsonNode blocks = message.get("blocks");
        if (blocks == null || !blocks.isArray()) {
            return null;
        }
        for (JsonNode block : blocks) {
            if (block.isObject() && isSimilarBlock(block)) {
                ObjectNode reference = similarReference(block, message);
                if (reference != null) {
                    return reference;
                }
            }
        }
        return null;
    }

    private ObjectNode similarReference(JsonNode block, JsonNode message) {
        String anchorCanonicalProductKey = canonicalProductKey(block.get("anchorCanonicalProductKey"));
        if (anchorCanonicalProductKey == null) {
            anchorCanonicalProductKey = productCanonicalProductKey(block.get("product"));
        }
        if (anchorCanonicalProductKey == null && message != null) {
            anchorCanonicalProductKey = canonicalProductKey(message.get("similarAnchorCanonicalProductKey"));
        }
        String query = firstOriginatingQuery(
                block.get("query"),
                message == null ? null : message.get("query")
        );
        if (anchorCanonicalProductKey == null || query == null) {
            return null;
        }

        Set<String> resultCanonicalProductKeys = resultCanonicalProductKeys(
                block, anchorCanonicalProductKey);
        if (resultCanonicalProductKeys.isEmpty()) {
            return null;
        }

        ObjectNode reference = objectMapper.createObjectNode();
        reference.put("type", "similar-reference");
        reference.put("anchorCanonicalProductKey", anchorCanonicalProductKey);
        ArrayNode resultKeys = reference.putArray("resultCanonicalProductKeys");
        resultCanonicalProductKeys.forEach(resultKeys::add);
        reference.put("query", query);
        String qualificationId = firstUuid(
                block.get("qualificationId"),
                message == null ? null : message.get("qualificationId")
        );
        if (qualificationId != null) {
            reference.put("qualificationId", qualificationId);
        }
        reference.put("status", "idle");
        return reference;
    }

    private Set<String> resultCanonicalProductKeys(JsonNode block, String anchorCanonicalProductKey) {
        Set<String> keys = new LinkedHashSet<>();
        addCanonicalProductKeys(keys, block.get("resultCanonicalProductKeys"), false, anchorCanonicalProductKey);
        if (keys.isEmpty()) {
            addCanonicalProductKeys(keys, block.get("products"), true, anchorCanonicalProductKey);
        }
        return keys;
    }

    private void addCanonicalProductKeys(
            Set<String> keys,
            JsonNode candidates,
            boolean products,
            String anchorCanonicalProductKey
    ) {
        if (candidates == null || !candidates.isArray()) {
            return;
        }
        for (JsonNode candidate : candidates) {
            String key = products ? productCanonicalProductKey(candidate) : canonicalProductKey(candidate);
            if (key == null || key.equals(anchorCanonicalProductKey)) {
                continue;
            }
            keys.add(key);
            if (keys.size() == MAX_SIMILAR_RESULTS) {
                return;
            }
        }
    }

    private String productCanonicalProductKey(JsonNode product) {
        if (product == null || !product.isObject()) {
            return null;
        }
        return firstCanonicalProductKey(
                product.path("canonicalProduct").get("key"),
                product.get("key"),
                product.get("id")
        );
    }

    private String firstCanonicalProductKey(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            String key = canonicalProductKey(candidate);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    private String canonicalProductKey(JsonNode value) {
        String key = text(value);
        if (key == null) {
            return null;
        }
        key = key.trim();
        return key.isEmpty() || key.length() > MAX_CANONICAL_PRODUCT_KEY_LENGTH ? null : key;
    }

    private String firstOriginatingQuery(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            String query = originatingQuery(candidate);
            if (query != null) {
                return query;
            }
        }
        return null;
    }

    private String originatingQuery(JsonNode value) {
        String query = text(value);
        if (query == null) {
            return null;
        }
        query = query.trim();
        return query.isEmpty() || query.length() > MAX_ORIGINATING_QUERY_LENGTH ? null : query;
    }

    private String firstUuid(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            String value = uuid(candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String uuid(JsonNode value) {
        String candidate = text(value);
        if (candidate == null) {
            return null;
        }
        try {
            String normalized = candidate.trim();
            return UUID.fromString(normalized).toString().equalsIgnoreCase(normalized)
                    ? normalized
                    : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String similarRole(JsonNode message) {
        String role = text(message.get("similarMessageRole"));
        if ("request".equals(role) || "response".equals(role)) {
            return role;
        }
        if (SIMILAR_AUTO_TITLE_SOURCE.equals(text(message.get("pendingOperation")))) {
            return "response";
        }
        if (containsSimilarBlock(message)) {
            return "response";
        }
        return null;
    }

    private boolean containsSimilarBlock(JsonNode message) {
        JsonNode blocks = message.get("blocks");
        if (blocks == null || !blocks.isArray()) {
            return false;
        }
        for (JsonNode block : blocks) {
            if (block.isObject() && isSimilarBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSimilarBlock(JsonNode block) {
        String type = text(block.get("type"));
        return "similar".equals(type) || "similar-reference".equals(type);
    }

    private String similarStatus(JsonNode value) {
        String status = text(value);
        return switch (status == null ? "" : status) {
            case "requested", "pending", "success", "empty", "error" -> status;
            default -> null;
        };
    }

    private String similarResponseText(String status) {
        return switch (status == null ? "" : status) {
            case "success" -> "I found similar products for your search.";
            case "empty" -> "I could not find another similar product for this search.";
            case "error" -> "I could not load similar products right now. Please try again.";
            default -> SIMILAR_PENDING_TEXT;
        };
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

    private void copyUuid(JsonNode source, ObjectNode target, String field) {
        String value = uuid(source.get(field));
        if (value != null) {
            target.put(field, value);
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

    private String text(JsonNode value) {
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private record SanitizedMessages(
            ArrayNode messages,
            boolean containsSimilarity,
            boolean containsNonSimilarity
    ) {
    }

    private record SanitizedSnapshot(String threadJson, boolean similarityGeneratedTitle) {
    }
}
