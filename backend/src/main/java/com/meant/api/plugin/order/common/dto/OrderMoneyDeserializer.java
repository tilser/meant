package com.meant.api.plugin.order.common.dto;

import com.meant.api.plugin.support.UcpMoney;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class OrderMoneyDeserializer extends ValueDeserializer<UcpOrderResponse.Money> {

    @Override
    public UcpOrderResponse.Money deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            Long amount = parser.currentToken() == JsonToken.VALUE_NUMBER_INT
                    ? UcpMoney.wholeNumberAmount(scalar(parser))
                    : majorAmount(parser, null);
            return new UcpOrderResponse.Money(amount, null);
        }
        String amount = null;
        String currency = null;
        boolean minorUnits = false;
        JsonToken amountToken = null;
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
                case "amount", "value", "price" -> {
                    amountToken = valueToken;
                    amount = scalar(parser);
                }
                case "minor_amount", "amount_minor", "amount_cents", "minorAmount", "amountMinor" -> {
                    amountToken = valueToken;
                    amount = scalar(parser);
                    minorUnits = true;
                }
                case "currency", "currency_code", "currencyCode" -> currency = scalar(parser);
                default -> parser.skipChildren();
            }
        }
        Long parsedAmount = minorUnits || amountToken == JsonToken.VALUE_NUMBER_INT
                ? UcpMoney.wholeNumberAmount(amount)
                : UcpMoney.minorAmount(amount, currency);
        return new UcpOrderResponse.Money(parsedAmount, currency);
    }

    private Long majorAmount(JsonParser parser, String currency) throws JacksonException {
        return UcpMoney.minorAmount(scalar(parser), currency);
    }

    private String scalar(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value;
    }
}
