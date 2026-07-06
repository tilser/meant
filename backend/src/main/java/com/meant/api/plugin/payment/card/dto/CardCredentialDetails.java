package com.meant.api.plugin.payment.card.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.dto.PaymentCredentialDetails;
import com.meant.api.plugin.payment.common.dto.PaymentScaLiability;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CardCredentialDetails(
        @JsonProperty("card_token")
        String cardToken,
        @JsonProperty("network_transaction_id")
        String networkTransactionId,
        @JsonProperty("three_ds_server_transaction_id")
        String threeDsServerTransactionId,
        String eci,
        String cryptogram,
        @JsonProperty("cryptogram_issued_at")
        Instant cryptogramIssuedAt,
        @JsonProperty("cryptogram_expires_at")
        Instant cryptogramExpiresAt,
        PaymentBinding binding,
        @JsonProperty("sca_liability")
        PaymentScaLiability scaLiability
) implements PaymentCredentialDetails {
}
