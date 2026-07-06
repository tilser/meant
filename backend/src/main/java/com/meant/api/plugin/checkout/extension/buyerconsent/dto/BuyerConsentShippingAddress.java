package com.meant.api.plugin.checkout.extension.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyerConsentShippingAddress(
        @JsonProperty("street_address")
        @JsonAlias({"streetAddress", "address1"})
        String streetAddress,
        @JsonProperty("address_locality")
        @JsonAlias({"addressLocality", "city"})
        String addressLocality,
        @JsonProperty("address_region")
        @JsonAlias({"addressRegion", "province", "provinceCode"})
        String addressRegion,
        @JsonProperty("postal_code")
        @JsonAlias({"postalCode", "zip"})
        String postalCode,
        @JsonProperty("address_country")
        @JsonAlias({"addressCountry", "country", "countryCode"})
        String addressCountry
) {
    public boolean isEmpty() {
        return isBlank(streetAddress)
                && isBlank(addressLocality)
                && isBlank(addressRegion)
                && isBlank(postalCode)
                && isBlank(addressCountry);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
