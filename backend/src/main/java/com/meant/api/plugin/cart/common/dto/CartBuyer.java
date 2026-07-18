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
public record CartBuyer(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonIgnore Map<String, JsonNode> extensions
) {
    public CartBuyer(String firstName, String lastName, String email, String phoneNumber) {
        this(firstName, lastName, email, phoneNumber, null);
    }

    public CartBuyer {
        extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
    }

    public boolean empty() {
        return firstName == null && lastName == null && email == null && phoneNumber == null && extensions.isEmpty();
    }

    public CartBuyer merge(CartBuyer override) {
        if (override == null || override.empty()) {
            return this;
        }
        Map<String, JsonNode> mergedExtensions = new LinkedHashMap<>(extensions);
        mergedExtensions.putAll(override.extensions);
        return new CartBuyer(
                first(override.firstName, firstName),
                first(override.lastName, lastName),
                first(override.email, email),
                first(override.phoneNumber, phoneNumber),
                mergedExtensions);
    }

    @JsonAnySetter
    public void putExtension(String name, JsonNode value) {
        extensions.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionValues() {
        return extensions;
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
