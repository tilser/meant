package com.meant.api.plugin.cart.common.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CartLineListDeserializer extends ValueDeserializer<List<UcpCartResponse.Line>> {

    @Override
    public List<UcpCartResponse.Line> deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_ARRAY) {
            return readLineArray(parser, context);
        }
        if (parser.currentToken() == JsonToken.START_OBJECT) {
            return readLineObject(parser, context);
        }

        parser.skipChildren();
        return List.of();
    }

    private List<UcpCartResponse.Line> readLineArray(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        List<UcpCartResponse.Line> lines = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
            if (token == JsonToken.START_OBJECT) {
                lines.addAll(readLineObject(parser, context));
            } else {
                parser.skipChildren();
            }
        }
        return lines;
    }

    private List<UcpCartResponse.Line> readLineObject(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        List<UcpCartResponse.Line> nestedLines = new ArrayList<>();
        String id = null;
        Integer quantity = null;
        UcpCartResponse.Cost cost = null;
        UcpCartResponse.Merchandise merchandise = null;

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
                case "edges" -> nestedLines.addAll(readEdgeArray(parser, context));
                case "nodes", "items", "line_items", "lineItems", "lines" -> {
                    if (parser.currentToken() == JsonToken.START_ARRAY) {
                        nestedLines.addAll(readLineArray(parser, context));
                    } else if (parser.currentToken() == JsonToken.START_OBJECT) {
                        nestedLines.addAll(readLineObject(parser, context));
                    } else {
                        parser.skipChildren();
                    }
                }
                case "node" -> {
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        nestedLines.addAll(readLineObject(parser, context));
                    } else {
                        parser.skipChildren();
                    }
                }
                case "id", "line_id", "lineId" -> id = scalarValue(parser);
                case "quantity" -> quantity = intValue(parser);
                case "cost" -> cost = context.readValue(parser, UcpCartResponse.Cost.class);
                case "merchandise", "item" -> merchandise = context.readValue(parser, UcpCartResponse.Merchandise.class);
                default -> parser.skipChildren();
            }
        }

        if (!nestedLines.isEmpty()) {
            return nestedLines;
        }
        if (id == null && quantity == null && cost == null && merchandise == null) {
            return List.of();
        }
        return List.of(new UcpCartResponse.Line(id, quantity, cost, merchandise));
    }

    private List<UcpCartResponse.Line> readEdgeArray(
            JsonParser parser,
            DeserializationContext context
    ) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            parser.skipChildren();
            return List.of();
        }

        List<UcpCartResponse.Line> lines = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != null && token != JsonToken.END_ARRAY) {
            if (token == JsonToken.START_OBJECT) {
                lines.addAll(readLineObject(parser, context));
            } else {
                parser.skipChildren();
            }
        }
        return lines;
    }

    private Integer intValue(JsonParser parser) throws JacksonException {
        String value = scalarValue(parser);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String scalarValue(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        String value = parser.getValueAsString();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
