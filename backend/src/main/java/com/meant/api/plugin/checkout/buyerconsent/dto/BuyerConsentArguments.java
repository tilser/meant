package com.meant.api.plugin.checkout.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BuyerConsentArguments(
        @JsonProperty("buyer_consent")
        BuyerConsentArtifact buyerConsent
) {
}
