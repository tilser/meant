package com.meant.api.module.agent.service;

import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Projects persisted agent JSON onto a buyer-safe boundary without changing ordinary payload
 * structure.
 */
public final class AgentBuyerPayloadSanitizer {

    private static final Set<String> TRANSPORT_COORDINATE_FIELDS = Set.of(
            "endpoint",
            "advertisedmcpendpoint",
            "profilemcpendpoint",
            "ucpurl",
            "profileendpoint",
            "routingdomain",
            "merchantdomain",
            "externalmerchantdomain",
            "uri"
    );
    private static final Set<String> VERIFIED_ORIGIN_FIELDS = Set.of(
            "merchantorigin"
    );
    private static final Set<String> EMBEDDED_JSON_FIELDS = Set.of(
            "payloadjson",
            "resultjson",
            "contentjson"
    );
    private static final String ROUTING_SCOPE_KEY = "routingScopeKey";
    private static final ObjectMapper JSON = new ObjectMapper();

    private AgentBuyerPayloadSanitizer() {
    }

    public static String sanitize(String payloadJson) {
        if (payloadJson == null || payloadJson.isEmpty()) {
            return payloadJson;
        }
        try {
            JsonNode parsed = JSON.readTree(payloadJson);
            if (parsed == null) {
                return MerchantBuyerTextSanitizer.sanitize(payloadJson);
            }
            return JSON.writeValueAsString(sanitizeNode(parsed, Context.empty()));
        } catch (JacksonException exception) {
            return MerchantBuyerTextSanitizer.sanitize(payloadJson);
        }
    }

    private static JsonNode sanitizeNode(JsonNode value, Context context) {
        if (value.isTextual()) {
            return JsonNodeFactory.instance.textNode(
                    sanitizeText(value.asText(), context)
            );
        }
        if (value.isObject()) {
            Context objectContext = contextFor((ObjectNode) value, context);
            ObjectNode sanitized = JsonNodeFactory.instance.objectNode();
            value.properties().forEach(entry -> {
                String field = entry.getKey();
                String normalizedField = field.toLowerCase(Locale.ROOT);
                if (TRANSPORT_COORDINATE_FIELDS.contains(normalizedField)) {
                    return;
                }
                if (ROUTING_SCOPE_KEY.equals(field) && entry.getValue().isTextual()) {
                    sanitized.put(
                            field,
                            BuyerSafeRoutingScopeKey.project(entry.getValue().asText())
                    );
                    return;
                }
                if (EMBEDDED_JSON_FIELDS.contains(normalizedField)
                        && entry.getValue().isTextual()) {
                    sanitized.put(
                            field,
                            sanitizeEmbeddedJson(entry.getValue().asText(), objectContext)
                    );
                    return;
                }
                sanitized.set(field, sanitizeNode(entry.getValue(), objectContext));
            });
            return sanitized;
        }
        if (value.isArray()) {
            ArrayNode sanitized = JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> sanitized.add(sanitizeNode(item, context)));
            return sanitized;
        }
        return value.deepCopy();
    }

    private static Context contextFor(ObjectNode value, Context parent) {
        String merchantOrigin = parent.merchantOrigin();
        List<String> aliases = new ArrayList<>(parent.technicalAliases());
        value.properties().forEach(entry -> {
            String field = entry.getKey().toLowerCase(Locale.ROOT);
            if (TRANSPORT_COORDINATE_FIELDS.contains(field)
                    && entry.getValue().isTextual()
                    && !VERIFIED_ORIGIN_FIELDS.contains(field)) {
                aliases.add(entry.getValue().asText());
            }
        });
        for (String field : VERIFIED_ORIGIN_FIELDS) {
            JsonNode candidate = fieldValue(value, field);
            if (candidate != null && candidate.isTextual()) {
                String verified =
                        MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(candidate.asText());
                if (verified != null) {
                    merchantOrigin = verified;
                    break;
                }
            }
        }
        return new Context(merchantOrigin, aliases);
    }

    private static JsonNode fieldValue(ObjectNode value, String normalizedField) {
        return value.properties()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(normalizedField))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String sanitizeText(String value, Context context) {
        String sanitized = MerchantBuyerTextSanitizer.sanitize(
                value,
                context.merchantOrigin(),
                null,
                (String) null
        );
        for (String alias : context.technicalAliases()) {
            sanitized = MerchantBuyerTextSanitizer.sanitize(
                    sanitized,
                    context.merchantOrigin(),
                    alias,
                    alias
            );
        }
        return sanitized;
    }

    private static String sanitizeEmbeddedJson(String value, Context context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            JsonNode parsed = JSON.readTree(value);
            return parsed == null
                    ? sanitizeText(value, context)
                    : JSON.writeValueAsString(sanitizeNode(parsed, context));
        } catch (JacksonException exception) {
            return sanitizeText(value, context);
        }
    }

    private record Context(String merchantOrigin, List<String> technicalAliases) {

        private Context {
            merchantOrigin =
                    MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(merchantOrigin);
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            if (technicalAliases != null) {
                technicalAliases.stream()
                        .filter(alias -> alias != null && !alias.isBlank())
                        .map(String::trim)
                        .forEach(aliases::add);
            }
            technicalAliases = List.copyOf(aliases);
        }

        private static Context empty() {
            return new Context(null, List.of());
        }
    }
}
