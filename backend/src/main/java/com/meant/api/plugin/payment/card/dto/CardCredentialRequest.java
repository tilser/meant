package com.meant.api.plugin.payment.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CardCredentialRequest(
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
        @NotBlank String currency,
        @JsonProperty("card_token")
        @NotBlank String cardToken,
        @JsonProperty("network_transaction_id")
        @NotBlank String networkTransactionId,
        @JsonProperty("three_ds_server_transaction_id")
        @NotBlank String threeDsServerTransactionId,
        @NotBlank String eci,
        @NotBlank String cryptogram,
        @JsonProperty("cryptogram_issued_at")
        @NotNull Instant cryptogramIssuedAt,
        @JsonProperty("cryptogram_expires_at")
        @NotNull Instant cryptogramExpiresAt,
        @JsonProperty("mandate_token")
        @NotBlank String mandateToken,
        @JsonProperty("validated_at")
        @NotNull Instant validatedAt,
        @JsonProperty("liability_shifted")
        boolean liabilityShifted,
        @JsonProperty("challenge_required")
        boolean challengeRequired
) {

    public PaymentBinding binding() {
        return new PaymentBinding(
                checkoutId,
                merchantId,
                merchantDomain,
                pspMerchantId,
                pspMerchantDomain,
                amountMinor,
                currency
        );
    }
}
