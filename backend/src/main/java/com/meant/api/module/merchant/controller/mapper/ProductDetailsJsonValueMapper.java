package com.meant.api.module.merchant.controller.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Isolates conversion of legacy catalog JSON values before controller response mapping. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductDetailsJsonValueMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        try {
            return JSON.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
