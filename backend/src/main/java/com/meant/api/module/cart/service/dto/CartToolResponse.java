package com.meant.api.module.cart.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

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
            String checkoutUrl,
            @JsonProperty("discount_codes")
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<AppliedCode> discountCodes,
            @JsonProperty("applied_discounts")
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<AppliedCode> appliedDiscounts,
            @JsonProperty("discount_allocations")
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<AppliedCode> discountAllocations,
            @JsonProperty("gift_card_codes")
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<AppliedCode> giftCardCodes,
            @JsonProperty("applied_gift_cards")
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<AppliedCode> appliedGiftCards,
            @JsonProperty("delivery_groups")
            List<DeliveryGroup> deliveryGroups
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
    public record AppliedCode(
            String code,
            String label,
            Boolean applicable,
            Money amount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryGroup(
            String id,
            String handle,
            @JsonProperty("delivery_options")
            List<DeliveryOption> deliveryOptions,
            @JsonProperty("selected_delivery_option")
            DeliveryOption selectedDeliveryOption
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryOption(
            String handle,
            String title,
            String description,
            String code,
            Money cost,
            @JsonProperty("cost_amount")
            Money costAmount,
            @JsonProperty("delivery_method_type")
            String deliveryMethodType,
            @JsonProperty("delivery_estimate")
            String deliveryEstimate,
            @JsonProperty("estimated_delivery_time")
            String estimatedDeliveryTime,
            @JsonProperty("estimated_delivery_at")
            Instant estimatedDeliveryAt,
            Boolean selected
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
