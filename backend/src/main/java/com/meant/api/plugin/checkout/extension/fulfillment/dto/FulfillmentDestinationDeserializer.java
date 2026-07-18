package com.meant.api.plugin.checkout.extension.fulfillment.dto;

import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentDestination;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.RetailLocation;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/** Resolves the schema's untagged destination union; retail locations require {@code name}. */
public class FulfillmentDestinationDeserializer extends ValueDeserializer<FulfillmentDestination> {

    @Override
    public FulfillmentDestination deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        JsonNode destination = context.readTree(parser);
        if (destination == null || !destination.isObject()) {
            return context.reportInputMismatch(
                    this,
                    "fulfillment destination must be a JSON object"
            );
        }
        Class<? extends FulfillmentDestination> destinationType = destination.hasNonNull("name")
                || destination.has("address")
                ? RetailLocation.class
                : ShippingDestination.class;
        return context.readTreeAsValue(destination, destinationType);
    }
}
