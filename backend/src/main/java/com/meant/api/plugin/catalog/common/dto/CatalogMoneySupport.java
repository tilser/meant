package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.support.UcpMoney;
import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

final class CatalogMoneySupport {

    private CatalogMoneySupport() {
    }

    static ParsedMoney parse(JsonParser parser) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            ParsedAmount amount = amount(parser, false);
            return new ParsedMoney(toMinor(amount, null), null);
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
                case "amount", "value", "price" -> amount = amount(parser, false);
                case "minor_amount", "amount_minor", "amount_cents", "minorAmount", "amountMinor" ->
                        amount = amount(parser, true);
                case "currency", "currency_code", "currencyCode" -> currency = scalar(parser);
                default -> parser.skipChildren();
            }
        }
        return new ParsedMoney(toMinor(amount, currency), currency);
    }

    private static Long toMinor(ParsedAmount amount, String currency) {
        if (amount == null) {
            return null;
        }
        return amount.minorUnits()
                ? wholeNumberAmount(amount.value())
                : UcpMoney.minorAmount(amount.value(), currency);
    }

    private static Long wholeNumberAmount(String value) {
        Long integer = UcpMoney.wholeNumberAmount(value);
        if (integer != null) {
            return integer;
        }
        try {
            return new BigDecimal(value.trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
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
        boolean wholeNumber = token == JsonToken.VALUE_NUMBER_INT
                || token == JsonToken.VALUE_NUMBER_FLOAT && wholeNumber(value)
                || token == JsonToken.VALUE_STRING && value.trim().matches("-?\\d+");
        return new ParsedAmount(value, explicitMinor || wholeNumber);
    }

    private static boolean wholeNumber(String value) {
        try {
            new BigDecimal(value).longValueExact();
            return true;
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private static String scalar(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value;
    }

    record ParsedMoney(Long amount, String currency) {
    }

    private record ParsedAmount(String value, boolean minorUnits) {
    }
}
