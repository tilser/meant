package com.meant.api.plugin.cart.common.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CartAppliedCodeListDeserializer extends ValueDeserializer<List<UcpCartResponse.AppliedCode>> {

    @Override
    public List<UcpCartResponse.AppliedCode> deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return List.of(new UcpCartResponse.AppliedCode(parser.getValueAsString(), null, null, null));
        }
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return List.of(readAppliedCode(parser));
        }
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }

        List<UcpCartResponse.AppliedCode> values = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
            if (token == JsonToken.VALUE_STRING) {
                values.add(new UcpCartResponse.AppliedCode(parser.getValueAsString(), null, null, null));
            } else if (token == JsonToken.START_OBJECT) {
                values.add(readAppliedCode(parser));
            } else {
                parser.skipChildren();
            }
        }
        return values;
    }

    private UcpCartResponse.AppliedCode readAppliedCode(JsonParser parser) throws JacksonException {
        String code = null;
        String label = null;
        Boolean applicable = null;
        UcpCartResponse.Money amount = null;

        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                parser.skipChildren();
                continue;
            }
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (fieldName == null || valueToken == null) {
                break;
            }
            switch (fieldName) {
                case "code", "last_characters" -> code = scalarValue(parser);
                case "title", "label", "name" -> label = scalarValue(parser);
                case "applicable", "valid" -> applicable = booleanValue(parser);
                case "amount", "discounted_amount", "amount_used", "applied_amount", "value" -> {
                    UcpCartResponse.Money candidate = moneyValue(parser);
                    if (candidate != null) {
                        amount = candidate;
                    }
                }
                default -> parser.skipChildren();
            }
        }

        return new UcpCartResponse.AppliedCode(code, label, applicable, amount);
    }

    private UcpCartResponse.Money moneyValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            Object amount = null;
            String currency = null;
            JsonToken token;
            while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
                if (token != JsonToken.PROPERTY_NAME) {
                    parser.skipChildren();
                    continue;
                }
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (fieldName == null || valueToken == null) {
                    break;
                }
                switch (fieldName) {
                    case "amount", "value", "minor_amount", "amount_minor", "amount_cents" -> amount = scalarValue(parser);
                    case "currency", "currency_code", "currencyCode" -> currency = scalarValue(parser);
                    default -> parser.skipChildren();
                }
            }
            return amount == null && currency == null ? null : new UcpCartResponse.Money(amount, currency);
        }
        String amount = scalarValue(parser);
        return amount == null ? null : new UcpCartResponse.Money(amount, null);
    }

    private String scalarValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private Boolean booleanValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (parser.currentToken() == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        String value = scalarValue(parser);
        return value == null ? null : Boolean.valueOf(value);
    }
}
