package com.meant.api.plugin.order.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpOrderResponse(
        String instructions,
        Order order,
        List<OrderMessage> messages,
        List<OrderError> errors
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
            @JsonAlias({"order_id", "orderId"})
            String id,
            String name,
            @JsonProperty("order_number")
            @JsonAlias("orderNumber")
            String orderNumber,
            String status,
            @JsonProperty("financial_status")
            @JsonAlias("financialStatus")
            String financialStatus,
            @JsonProperty("fulfillment_status")
            @JsonAlias("fulfillmentStatus")
            String fulfillmentStatus,
            String email,
            Customer customer,
            @JsonProperty("order_status_url")
            @JsonAlias("orderStatusUrl")
            String orderStatusUrl,
            @JsonProperty("created_at")
            @JsonAlias("createdAt")
            Instant createdAt,
            @JsonProperty("updated_at")
            @JsonAlias("updatedAt")
            Instant updatedAt,
            @JsonProperty("processed_at")
            @JsonAlias("processedAt")
            Instant processedAt,
            @JsonProperty("cancelled_at")
            @JsonAlias({"canceled_at", "cancelledAt", "canceledAt"})
            Instant canceledAt,
            @JsonProperty("closed_at")
            @JsonAlias("closedAt")
            Instant closedAt,
            @JsonProperty("line_items")
            @JsonAlias({"lineItems", "lines"})
            List<Line> lineItems,
            Cost cost,
            @JsonProperty("total_price")
            @JsonAlias({"totalPrice", "total_amount", "totalAmount"})
            Object totalPrice,
            @JsonProperty("subtotal_price")
            @JsonAlias({"subtotalPrice", "subtotal_amount", "subtotalAmount"})
            Object subtotalPrice,
            @JsonProperty("currency")
            @JsonAlias({"currency_code", "currencyCode"})
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(
            String id,
            String email
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            String id,
            String title,
            String name,
            String sku,
            String vendor,
            Integer quantity,
            @JsonProperty("product_id")
            @JsonAlias("productId")
            String productId,
            @JsonProperty("variant_id")
            @JsonAlias("variantId")
            String variantId,
            @JsonProperty("variant_title")
            @JsonAlias("variantTitle")
            String variantTitle,
            @JsonProperty("image_url")
            @JsonAlias({"imageUrl", "image"})
            String imageUrl,
            @JsonProperty("product_url")
            @JsonAlias({"productUrl", "url"})
            String productUrl,
            Object price,
            @JsonProperty("total_price")
            @JsonAlias({"totalPrice", "total_amount", "totalAmount"})
            Object totalPrice,
            @JsonProperty("currency")
            @JsonAlias({"currency_code", "currencyCode"})
            String currency,
            Product product,
            Variant variant
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String id,
            String title,
            String vendor,
            @JsonProperty("image_url")
            @JsonAlias({"imageUrl", "image"})
            String imageUrl,
            @JsonProperty("product_url")
            @JsonAlias({"productUrl", "url"})
            String productUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            String id,
            String title,
            String sku
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
    public record OrderMessage(
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
                    || matches(code, "order_not_found");
        }

        public boolean isNotFound() {
            return matches(code, "not_found") || matches(code, "order_not_found");
        }

        private boolean matches(String value, String expected) {
            return value != null && value.trim().equalsIgnoreCase(expected);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderError(
            String code,
            String message
    ) {
        public boolean isNotFound() {
            return code != null
                    && (code.trim().equalsIgnoreCase("not_found")
                    || code.trim().equalsIgnoreCase("order_not_found"));
        }
    }
}
