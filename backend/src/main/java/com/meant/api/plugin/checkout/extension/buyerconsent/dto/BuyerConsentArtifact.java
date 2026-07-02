package com.meant.api.plugin.checkout.extension.buyerconsent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BuyerConsentArtifact(
        @JsonProperty("consent_id")
        UUID consentId,
        @JsonProperty("user_id")
        UUID userId,
        @JsonProperty("merchant_id")
        UUID merchantId,
        @JsonProperty("checkout_id")
        String checkoutId,
        @JsonProperty("line_items")
        List<LineItem> lineItems,
        @JsonProperty("total_amount_minor")
        Long totalAmountMinor,
        String currency,
        @JsonProperty("tax_amount_minor")
        Long taxAmountMinor,
        @JsonProperty("shipping_address")
        Map<String, Object> shippingAddress,
        @JsonProperty("shipping_method")
        String shippingMethod,
        @JsonProperty("payment_instrument_hash")
        String paymentInstrumentHash,
        @JsonProperty("consented_at")
        Instant consentedAt,
        @JsonProperty("expires_at")
        Instant expiresAt,
        @JsonProperty("presented_terms_hash")
        String presentedTermsHash
) {

    public record LineItem(
            String id,
            @JsonProperty("product_variant_id")
            String productVariantId,
            Integer quantity,
            @JsonProperty("total_amount_minor")
            Long totalAmountMinor,
            String currency
    ) {
    }
}
