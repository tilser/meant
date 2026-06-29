package com.meant.api.plugin.checkout.common.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateBuyerConsentCommand(
        @NotNull UUID userId,
        @NotNull UUID merchantId,
        @NotBlank String checkoutId,
        @NotEmpty List<@Valid LineItem> lineItems,
        @NotNull Long totalAmountMinor,
        @NotBlank String currency,
        Long taxAmountMinor,
        Map<String, Object> shippingAddress,
        @NotBlank String shippingMethod,
        @NotBlank String paymentInstrumentReference,
        @NotNull Instant consentedAt,
        @NotNull @Future Instant expiresAt,
        @NotBlank String presentedTermsHash
) {

    public CreateBuyerConsentCommand {
        lineItems = lineItems == null ? null : List.copyOf(lineItems);
        shippingAddress = shippingAddress == null ? null : new LinkedHashMap<>(shippingAddress);
    }

    public record LineItem(
            @NotBlank String id,
            @NotBlank String productVariantId,
            @NotNull Integer quantity,
            @NotNull Long totalAmountMinor,
            @NotBlank String currency
    ) {
    }
}
