package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartToolResponse(
        String instructions,
        Cart cart,
        List<CartError> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cart(
            String id,
            @JsonProperty("created_at")
            Instant createdAt,
            @JsonProperty("updated_at")
            Instant updatedAt,
            List<Line> lines,
            Cost cost,
            @JsonProperty("total_quantity")
            Integer totalQuantity,
            @JsonProperty("checkout_url")
            String checkoutUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            String id,
            Integer quantity,
            Cost cost,
            Merchandise merchandise
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cost(
            @JsonProperty("total_amount")
            Money totalAmount,
            @JsonProperty("subtotal_amount")
            Money subtotalAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(
            String amount,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Merchandise(
            String id,
            String title,
            Product product
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String id,
            String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CartError(
            String message
    ) {
    }
}
