package com.meant.api.plugin.checkout.buyerconsent.dto;

import jakarta.validation.constraints.NotNull;

public record BuyerConsentRequest(
        @NotNull BuyerConsentArtifact artifact
) {
}
