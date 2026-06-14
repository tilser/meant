package com.meant.api.module.merchant.service.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class UcpResourceReferenceDeserializer extends ValueDeserializer<UcpResourceReference> {

    @Override
    public UcpResourceReference deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return new UcpResourceReference(parser.getValueAsString());
        }
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren();
            return null;
        }

        String url = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("url".equals(fieldName) && parser.currentToken() == JsonToken.VALUE_STRING) {
                url = parser.getValueAsString();
            } else {
                parser.skipChildren();
            }
        }
        return new UcpResourceReference(url);
    }
}
