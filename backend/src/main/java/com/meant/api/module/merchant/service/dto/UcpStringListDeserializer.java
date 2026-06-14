package com.meant.api.module.merchant.service.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class UcpStringListDeserializer extends ValueDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return List.of(parser.getValueAsString());
        }
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                values.add(parser.getValueAsString());
            } else {
                parser.skipChildren();
            }
        }
        return values;
    }
}
