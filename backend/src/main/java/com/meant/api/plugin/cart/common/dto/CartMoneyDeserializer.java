package com.meant.api.plugin.cart.common.dto;

import com.meant.api.plugin.support.UcpMoney;
import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

final class CartMoneyDeserializer extends ValueDeserializer<UcpCartResponse.Money> {

    @Override
    public UcpCartResponse.Money deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            ParsedAmount direct = amount(parser, false);
            return direct == null ? null : new UcpCartResponse.Money(toMinor(direct, null), null);
        }
        ParsedAmount amount = null;
        String currency = null;
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                parser.skipChildren();
                continue;
            }
            String field = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (field == null || valueToken == null) {
                break;
            }
            switch (field) {
                case "amount", "value" -> amount = amount(parser, false);
                case "minor_amount", "amount_minor", "amount_cents" -> amount = amount(parser, true);
                case "currency", "currency_code", "currencyCode" -> currency = scalar(parser);
                default -> parser.skipChildren();
            }
        }
        return amount == null && currency == null ? null
                : new UcpCartResponse.Money(toMinor(amount, currency), currency);
    }

    static Long decimalToMinor(String amount, String currency) {
        return UcpMoney.minorAmount(amount, currency);
    }

    static Long scalarToMinor(String amount, String currency, boolean explicitMinor) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        if (!explicitMinor) {
            return decimalToMinor(amount, currency);
        }
        try {
            return new BigDecimal(amount.trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private static Long toMinor(ParsedAmount amount, String currency) {
        return amount == null ? null : scalarToMinor(amount.value(), currency, amount.minorUnits());
    }

    private static ParsedAmount amount(JsonParser parser, boolean explicitMinor) throws JacksonException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return new ParsedAmount(value, explicitMinor || token == JsonToken.VALUE_NUMBER_INT);
    }

    private static String scalar(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private record ParsedAmount(String value, boolean minorUnits) {
    }
}
