package com.meant.api.plugin.checkout.extension.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuyerConsentState(
        Boolean analytics,
        Boolean preferences,
        Boolean marketing,
        @JsonProperty("sale_of_data")
        Boolean saleOfData
) {
    public boolean hasAnyConsentState() {
        return analytics != null || preferences != null || marketing != null || saleOfData != null;
    }
}
