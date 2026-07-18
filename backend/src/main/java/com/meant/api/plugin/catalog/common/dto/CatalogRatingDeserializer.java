package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.support.UcpDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CatalogRatingDeserializer extends ValueDeserializer<CatalogRating> {

    @Override
    public CatalogRating deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            Double value = decimal(parser);
            return value == null ? null : new CatalogRating(value, null, null);
        }
        Double value = null;
        Double scaleMax = null;
        Integer count = null;
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
                case "value", "average", "score", "rating", "rating_value", "ratingValue" -> value = decimal(parser);
                case "scale_max", "scaleMax", "max" -> scaleMax = decimal(parser);
                case "count", "review_count", "reviewCount", "reviews_count", "reviewsCount",
                        "rating_count", "ratingCount" -> count = nonNegativeInteger(parser);
                default -> parser.skipChildren();
            }
        }
        return value == null && scaleMax == null && count == null
                ? null
                : new CatalogRating(value, scaleMax, count);
    }

    private Double decimal(JsonParser parser) throws JacksonException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
            return null;
        }
        return UcpDecimal.decimalValue(parser.getValueAsString());
    }

    private Integer nonNegativeInteger(JsonParser parser) throws JacksonException {
        Double value = decimal(parser);
        return value == null ? null : Math.max(0, value.intValue());
    }
}
