package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Buyer consent binding for a checkout session before native completion.")
public record CreateCheckoutConsentRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("checkout_id")
        @NotBlank
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("payment_instrument_reference")
        @NotBlank
        String paymentInstrumentReference,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("shipping_method")
        String shippingMethod,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("presented_terms_hash")
        String presentedTermsHash
) {
}
