package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "CompleteCheckoutRequest")
public record CompleteCheckoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("buyer_consent_id")
        @NotNull
        UUID buyerConsentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("checkout_id")
        @NotBlank
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("payment_instruments")
        @NotEmpty
        List<@Valid PaymentInstrumentRequest> paymentInstruments,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("idempotency_key")
        String idempotencyKey,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("ap2_security_lock")
        boolean ap2SecurityLock,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("ap2_mandate")
        @Valid
        Ap2MandateRequest ap2Mandate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        CheckoutSignalsRequest signals
) {

    public CompleteCheckoutRequest {
        paymentInstruments = paymentInstruments == null ? null : List.copyOf(paymentInstruments);
    }

    @Schema(name = "CompleteCheckoutPaymentInstrumentRequest")
    public record PaymentInstrumentRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String handler,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("amount")
            @NotNull
            Long amountMinor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String currency,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @Valid
            PaymentCredentialRequest credential,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("sca_liability")
            @NotNull
            @Valid
            PaymentScaLiabilityRequest scaLiability
    ) {
    }

    @Schema(name = "CompleteCheckoutPaymentCredentialRequest")
    public record PaymentCredentialRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String token,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @Valid
            PaymentCredentialDetailsRequest details
    ) {
    }

    @Schema(name = "CompleteCheckoutPaymentCredentialDetailsRequest")
    public record PaymentCredentialDetailsRequest(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String source
    ) {
    }

    @Schema(name = "CompleteCheckoutPaymentScaLiabilityRequest")
    public record PaymentScaLiabilityRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("liable_party")
            @NotBlank
            String liableParty,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("liability_shifted")
            boolean liabilityShifted,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("challenge_required")
            boolean challengeRequired,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String reason
    ) {
    }

    @Schema(name = "CompleteCheckoutAp2MandateRequest")
    public record Ap2MandateRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("merchant_public_jwk")
            @NotNull
            @Valid
            JsonWebKeyRequest merchantPublicJwk,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("expected_merchant_authorization_kid")
            @NotBlank
            String expectedMerchantAuthorizationKid,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("merchant_authorization_issuer")
            @NotBlank
            String merchantAuthorizationIssuer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("agent_issuer")
            @NotBlank
            String agentIssuer,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String audience,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String nonce,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("expires_at")
            @NotNull
            Instant expiresAt,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("merchant_authorization_jws")
            String merchantAuthorizationJws
    ) {
    }

    @Schema(name = "CompleteCheckoutJsonWebKeyRequest")
    public record JsonWebKeyRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String kty,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String kid,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String crv,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String x,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String y,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String n,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String e,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String alg,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String use,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("key_ops")
            List<String> keyOps
    ) {
        public JsonWebKeyRequest {
            keyOps = keyOps == null ? List.of() : List.copyOf(keyOps);
        }
    }

    @Schema(name = "CompleteCheckoutSignalsRequest")
    public record CheckoutSignalsRequest(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("dev.meant.checkout_surface")
            String checkoutSurface,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @JsonProperty("user_agent")
            String userAgent
    ) {
    }
}
