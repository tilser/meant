package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCartResponse(
        String instructions,
        Cart cart,
        List<CartMessage> messages,
        List<CartError> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cart(
            String id,
            @JsonProperty("created_at")
            @JsonAlias("createdAt")
            Instant createdAt,
            @JsonProperty("updated_at")
            @JsonAlias("updatedAt")
            Instant updatedAt,
            @JsonProperty("expires_at")
            @JsonAlias({"expiresAt", "expiration", "expiration_time"})
            Instant expiresAt,
            @JsonDeserialize(using = CartLineListDeserializer.class)
            List<Line> lines,
            Cost cost,
            @JsonProperty("total_quantity")
            @JsonAlias("totalQuantity")
            Integer totalQuantity,
            @JsonProperty("checkout_url")
            @JsonAlias("checkoutUrl")
            String checkoutUrl,
            @JsonProperty("continue_url")
            @JsonAlias("continueUrl")
            String continueUrl,
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
            @JsonAlias("deliveryGroups")
            List<DeliveryGroup> deliveryGroups,
            List<CartMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            String id,
            Integer quantity,
            Cost cost,
            @JsonProperty("merchandise")
            @JsonAlias("item")
            Merchandise merchandise
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cost(
            @JsonProperty("total_amount")
            @JsonAlias("totalAmount")
            Money totalAmount,
            @JsonProperty("subtotal_amount")
            @JsonAlias("subtotalAmount")
            Money subtotalAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(
            Object amount,
            @JsonAlias({"currency_code", "currencyCode"})
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
            @JsonAlias("deliveryOptions")
            List<DeliveryOption> deliveryOptions,
            @JsonProperty("selected_delivery_option")
            @JsonAlias("selectedDeliveryOption")
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
            @JsonAlias("costAmount")
            Money costAmount,
            @JsonProperty("delivery_method_type")
            @JsonAlias("deliveryMethodType")
            String deliveryMethodType,
            @JsonProperty("delivery_estimate")
            @JsonAlias("deliveryEstimate")
            String deliveryEstimate,
            @JsonProperty("estimated_delivery_time")
            @JsonAlias("estimatedDeliveryTime")
            String estimatedDeliveryTime,
            @JsonProperty("estimated_delivery_at")
            @JsonAlias("estimatedDeliveryAt")
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
    public record CartMessage(
            String code,
            String severity,
            String type,
            String message,
            String target
    ) {
        public boolean isError() {
            return matches(severity, "error")
                    || matches(type, "error")
                    || matches(code, "not_found")
                    || matches(code, "cart_not_found");
        }

        public boolean isNotFound() {
            return matches(code, "not_found") || matches(code, "cart_not_found");
        }

        private boolean matches(String value, String expected) {
            return value != null && value.trim().equalsIgnoreCase(expected);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CartError(
            String code,
            String message
    ) {
        public boolean isNotFound() {
            return code != null
                    && (code.trim().equalsIgnoreCase("not_found")
                    || code.trim().equalsIgnoreCase("cart_not_found"));
        }
    }
}
