package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CompleteCheckoutRequest(
        @JsonProperty("buyer_consent_id")
        @NotNull UUID buyerConsentId,
        @JsonProperty("checkout_id")
        @NotBlank String checkoutId,
        @JsonProperty("payment_instruments")
        @NotEmpty List<@Valid PaymentInstrumentRequest> paymentInstruments,
        @JsonProperty("idempotency_key")
        String idempotencyKey,
        @JsonProperty("ap2_security_lock")
        boolean ap2SecurityLock,
        @JsonProperty("ap2_mandate")
        @Valid Ap2MandateRequest ap2Mandate,
        Map<String, Object> signals
) {

    public CompleteCheckoutRequest {
        paymentInstruments = paymentInstruments == null ? null : List.copyOf(paymentInstruments);
        signals = signals == null ? Map.of() : new LinkedHashMap<>(signals);
    }

    public record PaymentInstrumentRequest(
            @NotBlank String handler,
            @JsonProperty("amount")
            @NotNull Long amountMinor,
            @NotBlank String currency,
            @NotNull @Valid PaymentCredentialRequest credential,
            @JsonProperty("sca_liability")
            @NotNull @Valid PaymentScaLiabilityRequest scaLiability
    ) {
    }

    public record PaymentCredentialRequest(
            @NotBlank String type,
            @NotBlank String token,
            @NotNull Object details
    ) {
    }

    public record PaymentScaLiabilityRequest(
            @JsonProperty("liable_party")
            @NotBlank String liableParty,
            @JsonProperty("liability_shifted")
            boolean liabilityShifted,
            @JsonProperty("challenge_required")
            boolean challengeRequired,
            String reason
    ) {
    }

    public record Ap2MandateRequest(
            @JsonProperty("merchant_public_jwk")
            @NotEmpty Map<String, Object> merchantPublicJwk,
            @JsonProperty("expected_merchant_authorization_kid")
            @NotBlank String expectedMerchantAuthorizationKid,
            @JsonProperty("merchant_authorization_issuer")
            @NotBlank String merchantAuthorizationIssuer,
            @JsonProperty("agent_issuer")
            @NotBlank String agentIssuer,
            @NotBlank String audience,
            @NotBlank String nonce,
            @JsonProperty("expires_at")
            @NotNull Instant expiresAt,
            @JsonProperty("merchant_authorization_jws")
            String merchantAuthorizationJws
    ) {

        public Ap2MandateRequest {
            merchantPublicJwk = merchantPublicJwk == null ? Map.of() : new LinkedHashMap<>(merchantPublicJwk);
        }
    }
}
