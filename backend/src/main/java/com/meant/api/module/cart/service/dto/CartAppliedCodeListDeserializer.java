package com.meant.api.module.cart.service.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CartAppliedCodeListDeserializer extends ValueDeserializer<List<CartToolResponse.AppliedCode>> {

    @Override
    public List<CartToolResponse.AppliedCode> deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return List.of(new CartToolResponse.AppliedCode(parser.getValueAsString(), null, null, null));
        }
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return List.of(readAppliedCode(parser));
        }
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }

        List<CartToolResponse.AppliedCode> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                values.add(new CartToolResponse.AppliedCode(parser.getValueAsString(), null, null, null));
            } else if (parser.currentToken() == JsonToken.START_OBJECT) {
                values.add(readAppliedCode(parser));
            } else {
                parser.skipChildren();
            }
        }
        return values;
    }

    private CartToolResponse.AppliedCode readAppliedCode(JsonParser parser) throws JacksonException {
        String code = null;
        String label = null;
        Boolean applicable = null;
        CartToolResponse.Money amount = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "code", "last_characters" -> code = scalarValue(parser);
                case "title", "label", "name" -> label = scalarValue(parser);
                case "applicable", "valid" -> applicable = booleanValue(parser);
                case "amount", "discounted_amount", "amount_used", "applied_amount", "value" -> {
                    CartToolResponse.Money candidate = moneyValue(parser);
                    if (candidate != null) {
                        amount = candidate;
                    }
                }
                default -> parser.skipChildren();
            }
        }

        return new CartToolResponse.AppliedCode(code, label, applicable, amount);
    }

    private CartToolResponse.Money moneyValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            String amount = null;
            String currency = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "amount", "value" -> amount = scalarValue(parser);
                    case "currency", "currency_code", "currencyCode" -> currency = scalarValue(parser);
                    default -> parser.skipChildren();
                }
            }
            return amount == null && currency == null ? null : new CartToolResponse.Money(amount, currency);
        }
        String amount = scalarValue(parser);
        return amount == null ? null : new CartToolResponse.Money(amount, null);
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
