package com.meant.api.plugin.payment.shoppay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ShopPayCredentialRequest(
        @JsonProperty("checkout_id")
        @NotBlank String checkoutId,
        @JsonProperty("merchant_id")
        @NotBlank String merchantId,
        @JsonProperty("merchant_domain")
        @NotBlank String merchantDomain,
        @JsonProperty("shop_pay_merchant_id")
        @NotBlank String shopPayMerchantId,
        @JsonProperty("shop_pay_domain")
        @NotBlank String shopPayDomain,
        @JsonProperty("amount")
        @NotNull Long amountMinor,
        @NotBlank String currency,
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
                shopPayMerchantId,
                shopPayDomain,
                amountMinor,
                currency
        );
    }
}
