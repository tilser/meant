package com.meant.api.plugin.payment.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.support.PaymentBindingValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentBinding(
        @JsonProperty("checkout_id")
        @NotBlank String checkoutId,
        @JsonProperty("merchant_id")
        @NotBlank String merchantId,
        @JsonProperty("merchant_domain")
        @NotBlank String merchantDomain,
        @JsonProperty("psp_merchant_id")
        @NotBlank String pspMerchantId,
        @JsonProperty("psp_merchant_domain")
        @NotBlank String pspMerchantDomain,
        @JsonProperty("amount")
        @NotNull Long amountMinor,
        @NotBlank String currency
) {

    public PaymentBinding {
        checkoutId = PaymentBindingValidator.requireText(checkoutId, "checkoutId");
        merchantId = PaymentBindingValidator.requireText(merchantId, "merchantId");
        merchantDomain = PaymentBindingValidator.normalizedDomain(merchantDomain, "merchantDomain");
        pspMerchantId = PaymentBindingValidator.requireText(pspMerchantId, "pspMerchantId");
        pspMerchantDomain = PaymentBindingValidator.normalizedDomain(pspMerchantDomain, "pspMerchantDomain");
        amountMinor = PaymentBindingValidator.requireAmount(amountMinor, "amountMinor");
        currency = PaymentBindingValidator.normalizedCurrency(currency, "currency");
    }
}
