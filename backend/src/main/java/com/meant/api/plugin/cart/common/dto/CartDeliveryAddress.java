package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CartDeliveryAddress(
        String id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("street_address") String streetAddress,
        @JsonProperty("extended_address") String extendedAddress,
        @JsonProperty("address_locality") String addressLocality,
        @JsonProperty("address_region") String addressRegion,
        @JsonProperty("postal_code") String postalCode,
        @JsonProperty("address_country") String addressCountry,
        @JsonIgnore Map<String, JsonNode> extensions
) {
    public CartDeliveryAddress(
            String id, String firstName, String lastName, String phoneNumber, String streetAddress,
            String extendedAddress, String addressLocality, String addressRegion, String postalCode,
            String addressCountry
    ) {
        this(id, firstName, lastName, phoneNumber, streetAddress, extendedAddress, addressLocality,
                addressRegion, postalCode, addressCountry, null);
    }

    public CartDeliveryAddress {
        extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
    }

    public boolean empty() {
        return id == null && firstName == null && lastName == null && phoneNumber == null
                && streetAddress == null && extendedAddress == null && addressLocality == null
                && addressRegion == null && postalCode == null && addressCountry == null && extensions.isEmpty();
    }

    @JsonAnySetter
    public void putExtension(String name, JsonNode value) {
        extensions.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionValues() {
        return extensions;
    }
}
