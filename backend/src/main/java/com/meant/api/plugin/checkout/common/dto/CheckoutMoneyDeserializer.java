package com.meant.api.plugin.checkout.common.dto;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse.CheckoutMoney;
import com.meant.api.plugin.support.UcpMoney;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CheckoutMoneyDeserializer extends ValueDeserializer<CheckoutMoney> {

    @Override
    public CheckoutMoney deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return objectMoney(parser);
        }
        return scalarMoney(parser);
    }

    private CheckoutMoney objectMoney(JsonParser parser) throws JacksonException {
        Long minorAmount = null;
        String amount = null;
        String currency = null;
        String unit = null;

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
                case "minorAmount",
                     "minor_amount",
                     "amountMinor",
                     "amount_minor",
                     "amountInMinorUnits",
                     "amount_in_minor_units",
                     "amountCents",
                     "amount_cents",
                     "cents" -> minorAmount = firstPresent(minorAmount, wholeNumber(parser));
                case "amount", "value", "price", "min" -> {
                    MoneyScalar scalar = moneyScalar(parser);
                    if (scalar.minorAmount() != null) {
                        minorAmount = firstPresent(minorAmount, scalar.minorAmount());
                    }
                    if (scalar.amount() != null) {
                        amount = firstPresent(amount, scalar.amount());
                    }
                }
                case "currency", "currency_code", "currencyCode" -> currency = firstPresent(currency, scalarValue(parser));
                case "unit", "units", "amountUnit", "amount_unit", "scale", "format" -> unit = firstPresent(unit, scalarValue(parser));
                default -> parser.skipChildren();
            }
        }

        if (minorAmount == null && amount == null && currency == null && unit == null) {
            return null;
        }
        return new CheckoutMoney(minorAmount, amount, currency, unit);
    }

    private CheckoutMoney scalarMoney(JsonParser parser) throws JacksonException {
        MoneyScalar scalar = moneyScalar(parser);
        if (scalar.minorAmount() == null && scalar.amount() == null) {
            return null;
        }
        return new CheckoutMoney(scalar.minorAmount(), scalar.amount(), null, null);
    }

    private MoneyScalar moneyScalar(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return new MoneyScalar(wholeNumber(parser), null);
        }
        String value = scalarValue(parser);
        if (value == null) {
            return new MoneyScalar(null, null);
        }
        Long wholeNumber = UcpMoney.wholeNumberAmount(value);
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT || wholeNumber == null || value.contains(".")) {
            return new MoneyScalar(null, value);
        }
        return new MoneyScalar(wholeNumber, null);
    }

    private Long wholeNumber(JsonParser parser) throws JacksonException {
        String value = scalarValue(parser);
        return value == null ? null : UcpMoney.wholeNumberAmount(value);
    }

    private String scalarValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long firstPresent(Long first, Long second) {
        return first == null ? second : first;
    }

    private String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record MoneyScalar(Long minorAmount, String amount) {
    }
}
