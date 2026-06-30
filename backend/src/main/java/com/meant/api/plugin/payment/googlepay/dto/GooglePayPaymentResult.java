package com.meant.api.plugin.payment.googlepay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GooglePayPaymentResult(
        String status,
        @JsonProperty("payment_id")
        String paymentId,
        @JsonProperty("google_pay_merchant_id")
        String googlePayMerchantId,
        PaymentBinding binding,
        @JsonProperty("sca_liability")
        PaymentScaLiability scaLiability
) {
}
