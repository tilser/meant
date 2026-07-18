package com.meant.api.plugin.catalog.common.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CatalogStringListDeserializer extends ValueDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            String value = value(parser);
            return value == null ? List.of() : List.of(value);
        }
        List<String> values = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
            String value = value(parser);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String value(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return objectValue(parser);
        }
        if (parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String objectValue(JsonParser parser) throws JacksonException {
        String value = null;
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                parser.skipChildren();
                continue;
            }
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (field != null && valueToken != null && value == null && switch (field) {
                case "value", "name", "label", "title", "sku", "handle" -> true;
                default -> false;
            }) {
                value = value(parser);
            } else {
                parser.skipChildren();
            }
        }
        return value;
    }
}
