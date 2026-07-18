package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.support.UcpDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CatalogReviewCountDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return integer(parser);
        }
        Integer count = null;
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                parser.skipChildren();
                continue;
            }
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (field != null && valueToken != null && switch (field) {
                case "count", "value", "review_count", "reviewCount", "rating_count", "ratingCount" -> true;
                default -> false;
            }) {
                count = integer(parser);
            } else {
                parser.skipChildren();
            }
        }
        return count;
    }

    private Integer integer(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        Double value = UcpDecimal.decimalValue(parser.getValueAsString());
        return value == null ? null : Math.max(0, value.intValue());
    }
}
