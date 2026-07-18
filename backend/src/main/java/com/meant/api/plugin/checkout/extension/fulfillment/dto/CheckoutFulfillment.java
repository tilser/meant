package com.meant.api.plugin.checkout.extension.fulfillment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CheckoutFulfillment(
        List<FulfillmentMethod> methods,
        @JsonProperty("available_methods") List<FulfillmentAvailableMethod> availableMethods
) {

    public CheckoutFulfillment(List<FulfillmentMethod> methods) {
        this(methods, List.of());
    }

    public CheckoutFulfillment {
        methods = immutable(methods);
        availableMethods = immutable(availableMethods);
    }

    public boolean empty() {
        return methods.isEmpty() && availableMethods.isEmpty();
    }

    public sealed interface FulfillmentDestination permits ShippingDestination, RetailLocation {
        String id();
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentMethod(
            String id,
            String type,
            @JsonProperty("line_item_ids") List<String> lineItemIds,
            @JsonDeserialize(contentUsing = FulfillmentDestinationDeserializer.class)
            List<FulfillmentDestination> destinations,
            @JsonProperty("selected_destination_id") String selectedDestinationId,
            List<FulfillmentGroup> groups
    ) {
        public FulfillmentMethod {
            lineItemIds = immutable(lineItemIds);
            destinations = immutable(destinations);
            groups = immutable(groups);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ShippingDestination(
            String id,
            @JsonProperty("extended_address") String extendedAddress,
            @JsonProperty("street_address") String streetAddress,
            @JsonProperty("address_locality") String addressLocality,
            @JsonProperty("address_region") String addressRegion,
            @JsonProperty("address_country") String addressCountry,
            @JsonProperty("postal_code") String postalCode,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("phone_number") String phoneNumber
    ) implements FulfillmentDestination {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RetailLocation(
            String id,
            String name,
            PostalAddress address
    ) implements FulfillmentDestination {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PostalAddress(
            @JsonProperty("extended_address") String extendedAddress,
            @JsonProperty("street_address") String streetAddress,
            @JsonProperty("address_locality") String addressLocality,
            @JsonProperty("address_region") String addressRegion,
            @JsonProperty("address_country") String addressCountry,
            @JsonProperty("postal_code") String postalCode,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("phone_number") String phoneNumber
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentGroup(
            String id,
            @JsonProperty("line_item_ids") List<String> lineItemIds,
            List<FulfillmentOption> options,
            @JsonProperty("selected_option_id") String selectedOptionId
    ) {
        public FulfillmentGroup {
            lineItemIds = immutable(lineItemIds);
            options = immutable(options);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentOption(
            String id,
            String title,
            String description,
            String carrier,
            @JsonProperty("earliest_fulfillment_time") String earliestFulfillmentTime,
            @JsonProperty("latest_fulfillment_time") String latestFulfillmentTime,
            List<FulfillmentTotal> totals
    ) {
        public FulfillmentOption {
            totals = immutable(totals);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentTotal(
            String type,
            @JsonProperty("display_text") String displayText,
            Long amount
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FulfillmentAvailableMethod(
            String type,
            @JsonProperty("line_item_ids") List<String> lineItemIds,
            @JsonProperty("fulfillable_on") String fulfillableOn,
            String description
    ) {
        public FulfillmentAvailableMethod {
            lineItemIds = immutable(lineItemIds);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
