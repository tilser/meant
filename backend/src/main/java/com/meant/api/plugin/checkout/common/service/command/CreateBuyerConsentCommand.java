package com.meant.api.plugin.checkout.common.service.command;

import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateBuyerConsentCommand(
        @NotNull UUID userId,
        @NotNull UUID merchantId,
        @NotBlank String checkoutId,
        @NotEmpty List<@Valid LineItem> lineItems,
        @NotNull Long totalAmountMinor,
        @NotBlank String currency,
        Long taxAmountMinor,
        BuyerConsentShippingAddress shippingAddress,
        @NotBlank String shippingMethod,
        @NotBlank String paymentInstrumentReference,
        @NotNull Instant consentedAt,
        @NotNull @Future Instant expiresAt,
        @NotBlank String presentedTermsHash
) {

    public CreateBuyerConsentCommand {
        lineItems = lineItems == null ? null : List.copyOf(lineItems);
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
