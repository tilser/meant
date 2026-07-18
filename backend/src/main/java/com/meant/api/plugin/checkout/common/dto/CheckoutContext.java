package com.meant.api.plugin.checkout.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutContext(
        @JsonProperty("address_country") String addressCountry,
        @JsonProperty("address_region") String addressRegion,
        @JsonProperty("postal_code") String postalCode,
        String intent,
        String language,
        String currency,
        List<String> eligibility,
        @JsonIgnore Map<String, JsonNode> extensions
) {

    public CheckoutContext(
            String addressCountry,
            String addressRegion,
            String postalCode,
            String intent,
            String language,
            String currency,
            List<String> eligibility
    ) {
        this(addressCountry, addressRegion, postalCode, intent, language, currency, eligibility, null);
    }

    public CheckoutContext {
        eligibility = eligibility == null ? List.of() : List.copyOf(eligibility);
        extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
    }

    public static CheckoutContext none() {
        return new CheckoutContext(null, null, null, null, null, null, List.of());
    }

    public boolean empty() {
        return isBlank(addressCountry)
                && isBlank(addressRegion)
                && isBlank(postalCode)
                && isBlank(intent)
                && isBlank(language)
                && isBlank(currency)
                && eligibility.isEmpty()
                && extensions.isEmpty();
    }

    @JsonAnySetter
    public void putExtension(String name, JsonNode value) {
        extensions.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionValues() {
        return extensions;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
