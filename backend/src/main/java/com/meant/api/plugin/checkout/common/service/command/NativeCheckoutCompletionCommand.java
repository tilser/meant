package com.meant.api.plugin.checkout.common.service.command;

import com.meant.api.plugin.checkout.complete.dto.CheckoutSignals;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import com.meant.api.plugin.signing.JsonWebKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NativeCheckoutCompletionCommand(
        @NotNull UUID cartId,
        @NotNull UUID userId,
        @NotNull UUID buyerConsentId,
        @NotBlank String checkoutId,
        @NotEmpty List<@Valid PaymentInstrument> paymentInstruments,
        String idempotencyKey,
        boolean ap2SecurityLock,
        @Valid Ap2MandateInput ap2Mandate,
        CheckoutSignals signals
) {

    public NativeCheckoutCompletionCommand {
        paymentInstruments = paymentInstruments == null ? null : List.copyOf(paymentInstruments);
    }

    public record Ap2MandateInput(
            @NotNull @Valid JsonWebKey merchantPublicJwk,
            @NotBlank String expectedMerchantAuthorizationKid,
            @NotBlank String merchantAuthorizationIssuer,
            @NotBlank String agentIssuer,
            @NotBlank String audience,
            @NotBlank String nonce,
            @NotNull Instant expiresAt,
            String merchantAuthorizationJws
    ) {
    }
}
