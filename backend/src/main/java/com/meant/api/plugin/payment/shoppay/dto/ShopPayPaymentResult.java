package com.meant.api.plugin.payment.shoppay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShopPayPaymentResult(
        String status,
        @JsonProperty("payment_id")
        String paymentId,
        @JsonProperty("shop_pay_charge_id")
        String shopPayChargeId,
        PaymentBinding binding,
        @JsonProperty("sca_liability")
        PaymentScaLiability scaLiability
) {
}
