package com.meant.api.plugin.payment.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardPaymentResult(
        String status,
        @JsonProperty("payment_id")
        String paymentId,
        @JsonProperty("network_transaction_id")
        String networkTransactionId,
        PaymentBinding binding,
        @JsonProperty("sca_liability")
        PaymentScaLiability scaLiability
) {
}
