package com.meant.api.plugin.checkout.extension.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyerWithConsent(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String email,
        @JsonProperty("phone_number") String phoneNumber,
        BuyerConsentState consent
) {

    public static BuyerWithConsent from(CheckoutBuyer buyer, BuyerConsentState consent) {
        if (buyer == null && (consent == null || !consent.hasAnyConsentState())) {
            return null;
        }
        return new BuyerWithConsent(
                buyer == null ? null : buyer.firstName(),
                buyer == null ? null : buyer.lastName(),
                buyer == null ? null : buyer.email(),
                buyer == null ? null : buyer.phoneNumber(),
                consent != null && consent.hasAnyConsentState() ? consent : null
        );
    }
}
