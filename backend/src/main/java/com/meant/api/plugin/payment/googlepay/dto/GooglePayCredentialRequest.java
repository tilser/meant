package com.meant.api.plugin.payment.googlepay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GooglePayCredentialRequest(
        @JsonProperty("checkout_id")
        @NotBlank String checkoutId,
        @JsonProperty("merchant_id")
        @NotBlank String merchantId,
        @JsonProperty("merchant_domain")
        @NotBlank String merchantDomain,
        @JsonProperty("google_pay_merchant_id")
        @NotBlank String googlePayMerchantId,
        @JsonProperty("gateway_merchant_id")
        @NotBlank String gatewayMerchantId,
        @JsonProperty("google_pay_domain")
        @NotBlank String googlePayDomain,
        @JsonProperty("amount")
        @NotNull Long amountMinor,
        @NotBlank String currency,
        @JsonProperty("protocol_version")
        @NotBlank String protocolVersion,
        @NotBlank String signature,
        @JsonProperty("signed_message")
        @NotBlank String signedMessage,
        @NotBlank String cryptogram,
        @JsonProperty("cryptogram_issued_at")
        @NotNull Instant cryptogramIssuedAt,
        @JsonProperty("cryptogram_expires_at")
        @NotNull Instant cryptogramExpiresAt,
        @JsonProperty("mandate_token")
        @NotBlank String mandateToken,
        @JsonProperty("validated_at")
        @NotNull Instant validatedAt
) {

    public PaymentBinding binding() {
        return new PaymentBinding(
                checkoutId,
                merchantId,
                merchantDomain,
                gatewayMerchantId,
                googlePayDomain,
                amountMinor,
                currency
        );
    }
}
