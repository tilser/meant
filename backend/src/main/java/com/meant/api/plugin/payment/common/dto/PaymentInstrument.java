package com.meant.api.plugin.payment.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentInstrument(
        @NotBlank String handler,
        @JsonProperty("amount")
        @NotNull Long amountMinor,
        @NotBlank String currency,
        @NotNull @Valid PaymentCredential credential,
        @JsonProperty("sca_liability")
        @NotNull PaymentScaLiability scaLiability
) {

    public PaymentInstrument {
        handler = PaymentBindingValidator.requireText(handler, "handler");
        amountMinor = PaymentBindingValidator.requireAmount(amountMinor, "amountMinor");
        currency = PaymentBindingValidator.normalizedCurrency(currency, "currency");
    }
}
