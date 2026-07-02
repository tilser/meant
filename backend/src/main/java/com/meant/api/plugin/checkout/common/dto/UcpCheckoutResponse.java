package com.meant.api.plugin.checkout.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.extension.ap2mandate.dto.Ap2CheckoutData;
import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCheckoutResponse(
        String instructions,
        Checkout checkout,
        @JsonProperty("checkout_id")
        @JsonAlias({"checkoutId", "id"})
        String checkoutId,
        @JsonProperty("cart_id")
        @JsonAlias("cartId")
        String cartId,
        String status,
        @JsonProperty("checkout_url")
        @JsonAlias("checkoutUrl")
        String checkoutUrl,
        @JsonProperty("continue_url")
        @JsonAlias("continueUrl")
        String continueUrl,
        @JsonProperty("order_id")
        @JsonAlias({"orderId", "order_ref", "orderRef"})
        String orderId,
        Map<String, Object> order,
        @JsonProperty("created_at")
        @JsonAlias("createdAt")
        Instant createdAt,
        @JsonProperty("updated_at")
        @JsonAlias("updatedAt")
        Instant updatedAt,
        @JsonProperty("expires_at")
        @JsonAlias({"expiresAt", "expiration", "expiration_time"})
        Instant expiresAt,
        String currency,
        @JsonProperty("line_items")
        @JsonAlias("lineItems")
        List<Map<String, Object>> lineItems,
        List<Map<String, Object>> totals,
        Map<String, Object> buyer,
        CheckoutDiscounts discounts,
        CheckoutFulfillment fulfillment,
        Ap2CheckoutData ap2,
        List<CheckoutMessage> messages,
        List<CheckoutError> errors
) {

    public Checkout resolvedCheckout() {
        if (checkout != null) {
            return checkout;
        }
        if (checkoutId == null && cartId == null && checkoutUrl == null && continueUrl == null) {
            return null;
        }
        return new Checkout(
                checkoutId,
                cartId,
                status,
                checkoutUrl,
                continueUrl,
                orderId,
                order,
                createdAt,
                updatedAt,
                expiresAt,
                currency,
                lineItems,
                totals,
                buyer,
                discounts,
                fulfillment,
                ap2,
                List.of()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Checkout(
            String id,
            @JsonProperty("cart_id")
            @JsonAlias("cartId")
            String cartId,
            String status,
            @JsonProperty("checkout_url")
            @JsonAlias("checkoutUrl")
            String checkoutUrl,
            @JsonProperty("continue_url")
            @JsonAlias("continueUrl")
            String continueUrl,
            @JsonProperty("order_id")
            @JsonAlias({"orderId", "order_ref", "orderRef"})
            String orderId,
            Map<String, Object> order,
            @JsonProperty("created_at")
            @JsonAlias("createdAt")
            Instant createdAt,
            @JsonProperty("updated_at")
            @JsonAlias("updatedAt")
            Instant updatedAt,
            @JsonProperty("expires_at")
            @JsonAlias({"expiresAt", "expiration", "expiration_time"})
            Instant expiresAt,
            String currency,
            @JsonProperty("line_items")
            @JsonAlias("lineItems")
            List<Map<String, Object>> lineItems,
            List<Map<String, Object>> totals,
            Map<String, Object> buyer,
            CheckoutDiscounts discounts,
            CheckoutFulfillment fulfillment,
            Ap2CheckoutData ap2,
            List<CheckoutMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutMessage(
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
                    || matches(code, "checkout_not_found")
                    || matches(code, "cart_not_found");
        }

        public boolean isNotFound() {
            return matches(code, "not_found")
                    || matches(code, "checkout_not_found")
                    || matches(code, "cart_not_found");
        }

        public boolean isRecoverable() {
            return matches(severity, "recoverable")
                    || matches(type, "recoverable")
                    || matches(code, "recoverable")
                    || matches(code, "temporarily_unavailable")
                    || matches(code, "retryable");
        }

        public boolean isUnrecoverable() {
            return matches(severity, "unrecoverable")
                    || matches(type, "unrecoverable")
                    || matches(code, "unrecoverable")
                    || matches(code, "payment_declined")
                    || matches(code, "mandate_required")
                    || matches(code, "charge_mismatch");
        }

        private boolean matches(String value, String expected) {
            return value != null && value.trim().equalsIgnoreCase(expected);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutError(
            String code,
            String message
    ) {
        public boolean isNotFound() {
            return code != null
                    && (code.trim().equalsIgnoreCase("not_found")
                    || code.trim().equalsIgnoreCase("checkout_not_found")
                    || code.trim().equalsIgnoreCase("cart_not_found"));
        }
    }
}
