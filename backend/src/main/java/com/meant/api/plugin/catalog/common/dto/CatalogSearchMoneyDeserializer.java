package com.meant.api.plugin.catalog.common.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class CatalogSearchMoneyDeserializer extends ValueDeserializer<CatalogSearchResponse.Money> {

    @Override
    public CatalogSearchResponse.Money deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        CatalogMoneySupport.ParsedMoney parsed = CatalogMoneySupport.parse(parser);
        return new CatalogSearchResponse.Money(parsed.amount(), parsed.currency());
    }
}
