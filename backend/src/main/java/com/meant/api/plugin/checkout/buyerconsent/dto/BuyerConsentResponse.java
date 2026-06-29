package com.meant.api.plugin.checkout.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record BuyerConsentResponse(
        boolean accepted,
        @JsonProperty("consent_id")
        UUID consentId,
        List<String> messages
) {
}
