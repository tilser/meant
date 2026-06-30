package com.meant.api.plugin.payment.shoppay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShopPayCredentialDetails(
        @JsonProperty("shop_pay_merchant_id")
        String shopPayMerchantId,
        @JsonProperty("shop_pay_domain")
        String shopPayDomain,
        String cryptogram,
        @JsonProperty("cryptogram_issued_at")
        Instant cryptogramIssuedAt,
        @JsonProperty("cryptogram_expires_at")
        Instant cryptogramExpiresAt,
        PaymentBinding binding
) {
}
