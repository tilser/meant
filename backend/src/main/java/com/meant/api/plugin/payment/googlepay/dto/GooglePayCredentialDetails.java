package com.meant.api.plugin.payment.googlepay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GooglePayCredentialDetails(
        @JsonProperty("google_pay_merchant_id")
        String googlePayMerchantId,
        @JsonProperty("gateway_merchant_id")
        String gatewayMerchantId,
        @JsonProperty("google_pay_domain")
        String googlePayDomain,
        @JsonProperty("protocol_version")
        String protocolVersion,
        String signature,
        @JsonProperty("signed_message")
        String signedMessage,
        String cryptogram,
        @JsonProperty("cryptogram_issued_at")
        Instant cryptogramIssuedAt,
        @JsonProperty("cryptogram_expires_at")
        Instant cryptogramExpiresAt,
        PaymentBinding binding
) {
}
