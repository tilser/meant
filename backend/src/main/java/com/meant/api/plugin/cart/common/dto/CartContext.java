package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CartContext(
        @JsonProperty("address_country") String addressCountry,
        @JsonProperty("address_region") String addressRegion,
        @JsonProperty("postal_code") String postalCode,
        String intent,
        String language,
        String currency,
        List<String> eligibility,
        @JsonIgnore Map<String, JsonNode> extensions
) {
    public CartContext(
            String addressCountry, String addressRegion, String postalCode, String intent,
            String language, String currency, List<String> eligibility
    ) {
        this(addressCountry, addressRegion, postalCode, intent, language, currency, eligibility, null);
    }

    public CartContext {
        eligibility = eligibility == null ? List.of() : List.copyOf(eligibility);
        extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
    }

    public CartContext(String addressCountry) {
        this(addressCountry, null, null, null, null, null, List.of());
    }

    public boolean empty() {
        return addressCountry == null && addressRegion == null && postalCode == null
                && intent == null && language == null && currency == null && eligibility.isEmpty()
                && extensions.isEmpty();
    }

    public CartContext merge(CartContext override) {
        if (override == null || override.empty()) {
            return this;
        }
        return new CartContext(
                first(override.addressCountry, addressCountry),
                first(override.addressRegion, addressRegion),
                first(override.postalCode, postalCode),
                first(override.intent, intent),
                first(override.language, language),
                first(override.currency, currency),
                override.eligibility.isEmpty() ? eligibility : override.eligibility,
                mergeExtensions(extensions, override.extensions)
        );
    }

    @JsonAnySetter
    public void putExtension(String name, JsonNode value) {
        extensions.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionValues() {
        return extensions;
    }

    private static Map<String, JsonNode> mergeExtensions(
            Map<String, JsonNode> current, Map<String, JsonNode> override) {
        Map<String, JsonNode> merged = new LinkedHashMap<>(current);
        merged.putAll(override);
        return merged;
    }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
