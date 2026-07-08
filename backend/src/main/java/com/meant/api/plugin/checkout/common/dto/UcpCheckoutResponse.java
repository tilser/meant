package com.meant.api.plugin.checkout.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.extension.discount.dto.AppliedDiscount;
import com.meant.api.plugin.checkout.extension.ap2mandate.dto.Ap2CheckoutData;
import com.meant.api.plugin.support.UcpMoney;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCheckoutResponse(
        UcpMetadata ucp,
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
        CheckoutOrder order,
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
        List<CheckoutLineItem> lineItems,
        List<CheckoutTotal> totals,
        CheckoutBuyer buyer,
        CheckoutDiscounts discounts,
        CheckoutFulfillment fulfillment,
        Ap2CheckoutData ap2,
        @JsonProperty("total_amount")
        @JsonAlias({"totalAmount", "grand_total", "grandTotal"})
        @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
        CheckoutMoney totalAmount,
        @JsonProperty("tax_amount")
        @JsonAlias({"taxAmount", "totalTaxAmount", "tax"})
        @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
        CheckoutMoney taxAmount,
        @JsonProperty("shipping_address")
        @JsonAlias({"shippingAddress", "deliveryAddress"})
        CheckoutAddress shippingAddress,
        @JsonProperty("shipping_method")
        @JsonAlias({"shippingMethod", "selectedShippingMethod", "deliveryMethod"})
        CheckoutShippingMethod shippingMethod,
        List<CheckoutMessage> messages,
        List<CheckoutError> errors
) {

    public UcpCheckoutResponse {
        lineItems = safeList(lineItems);
        totals = safeList(totals);
        messages = safeList(messages);
        errors = safeList(errors);
    }

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
                totalAmount,
                taxAmount,
                shippingAddress,
                shippingMethod,
                List.of()
        );
    }

    public String version() {
        return ucp == null ? null : ucp.version();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UcpMetadata(String version) {
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
            CheckoutOrder order,
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
            List<CheckoutLineItem> lineItems,
            List<CheckoutTotal> totals,
            CheckoutBuyer buyer,
            CheckoutDiscounts discounts,
            CheckoutFulfillment fulfillment,
            Ap2CheckoutData ap2,
            @JsonProperty("total_amount")
            @JsonAlias({"totalAmount", "grand_total", "grandTotal"})
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney totalAmount,
            @JsonProperty("tax_amount")
            @JsonAlias({"taxAmount", "totalTaxAmount", "tax"})
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney taxAmount,
            @JsonProperty("shipping_address")
            @JsonAlias({"shippingAddress", "deliveryAddress"})
            CheckoutAddress shippingAddress,
            @JsonProperty("shipping_method")
            @JsonAlias({"shippingMethod", "selectedShippingMethod", "deliveryMethod"})
            CheckoutShippingMethod shippingMethod,
            List<CheckoutMessage> messages
    ) {

        public Checkout {
            lineItems = safeList(lineItems);
            totals = safeList(totals);
            messages = safeList(messages);
        }

        public UcpMoney resolvedTotal() {
            UcpMoney direct = totalAmount == null ? null : totalAmount.toUcpMoney(currency);
            if (direct != null) {
                return direct;
            }
            return CheckoutTotal.totalMoney(totals, currency);
        }

        public Long resolvedTaxAmountMinor() {
            UcpMoney direct = taxAmount == null ? null : taxAmount.toUcpMoney(currency);
            return direct == null ? null : direct.amount();
        }

        public String resolvedCurrency(String fallbackCurrency) {
            UcpMoney total = resolvedTotal();
            return firstPresent(total == null ? null : total.currency(), currency, fallbackCurrency);
        }

        public String resolvedOrderRef() {
            return firstPresent(orderId, order == null ? null : order.resolvedRef());
        }

        public CheckoutAddress resolvedShippingAddress() {
            if (shippingAddress != null) {
                return shippingAddress;
            }
            return fulfillment == null ? null : fulfillment.resolvedShippingAddress();
        }

        public String resolvedShippingMethod() {
            if (shippingMethod != null) {
                return shippingMethod.resolvedName();
            }
            return fulfillment == null ? null : fulfillment.resolvedShippingMethod();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutOrder(
            String id,
            @JsonProperty("order_id")
            @JsonAlias("orderId")
            String orderId,
            @JsonProperty("order_ref")
            @JsonAlias("orderRef")
            String orderRef,
            String name,
            String reference,
            String ref
    ) {

        public String resolvedRef() {
            return firstPresent(id, orderId, orderRef, name, reference, ref);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutBuyer(
            @JsonProperty("first_name")
            @JsonAlias("firstName")
            String firstName,
            @JsonProperty("last_name")
            @JsonAlias("lastName")
            String lastName,
            String email,
            @JsonProperty("phone_number")
            @JsonAlias("phoneNumber")
            String phoneNumber
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutLineItem(
            String id,
            @JsonProperty("line_id")
            @JsonAlias("lineId")
            String lineId,
            @JsonProperty("product_variant_id")
            @JsonAlias({"productVariantId", "variant_id", "variantId"})
            String productVariantId,
            CheckoutItem item,
            CheckoutItem merchandise,
            @JsonAlias("qty")
            Integer quantity,
            @JsonProperty("total_amount")
            @JsonAlias("totalAmount")
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney totalAmount,
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney total,
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney amount,
            List<CheckoutTotal> totals,
            String currency
    ) {

        public CheckoutLineItem {
            totals = safeList(totals);
        }

        public String resolvedId() {
            return firstPresent(id, lineId, resolvedVariantId());
        }

        public String resolvedVariantId() {
            return firstPresent(
                    productVariantId,
                    merchandise == null ? null : merchandise.id(),
                    item == null ? null : item.id()
            );
        }

        public UcpMoney resolvedTotal(String fallbackCurrency) {
            String lineCurrency = firstPresent(currency, fallbackCurrency);
            UcpMoney direct = firstMoney(lineCurrency, totalAmount, total, amount);
            return direct == null ? CheckoutTotal.totalMoney(totals, lineCurrency) : direct;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutItem(
            String id,
            String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutTotal(
            String type,
            String code,
            String name,
            @JsonProperty("total_amount")
            @JsonAlias("totalAmount")
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney totalAmount,
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney total,
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney amount,
            @JsonDeserialize(using = CheckoutMoneyDeserializer.class)
            CheckoutMoney value,
            String currency
    ) {

        public UcpMoney resolvedMoney(String fallbackCurrency) {
            return firstMoney(firstPresent(currency, fallbackCurrency), totalAmount, total, amount, value);
        }

        public boolean isTotal() {
            String normalized = firstPresent(type, code, name);
            return normalized == null
                    || normalized.equalsIgnoreCase("total")
                    || normalized.equalsIgnoreCase("grand_total")
                    || normalized.equalsIgnoreCase("grandTotal");
        }

        private static UcpMoney totalMoney(List<CheckoutTotal> totals, String fallbackCurrency) {
            if (totals == null) {
                return null;
            }
            for (CheckoutTotal total : totals) {
                if (total != null && total.isTotal()) {
                    UcpMoney money = total.resolvedMoney(fallbackCurrency);
                    if (money != null) {
                        return money;
                    }
                }
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutMoney(
            Long minorAmount,
            String amount,
            String currency,
            String unit
    ) {

        public UcpMoney toUcpMoney(String fallbackCurrency) {
            String resolvedCurrency = firstPresent(currency, fallbackCurrency);
            if (minorAmount != null) {
                return new UcpMoney(minorAmount, resolvedCurrency);
            }
            if (amount == null) {
                return null;
            }
            Long parsedAmount;
            if (hasMinorUnitHint(unit)) {
                parsedAmount = UcpMoney.wholeNumberAmount(amount);
            } else {
                if (resolvedCurrency == null) {
                    return null;
                }
                parsedAmount = UcpMoney.minorAmount(amount, resolvedCurrency);
            }
            return parsedAmount == null ? null : new UcpMoney(parsedAmount, resolvedCurrency);
        }

        private static boolean hasMinorUnitHint(String unit) {
            if (unit == null || unit.isBlank()) {
                return false;
            }
            String normalized = unit.trim().toLowerCase(java.util.Locale.ROOT);
            return normalized.contains("minor")
                    || normalized.equals("cent")
                    || normalized.equals("cents")
                    || normalized.equals("centavo")
                    || normalized.equals("centavos");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutAddress(
            String id,
            @JsonProperty("street_address")
            @JsonAlias({"streetAddress", "address1"})
            String streetAddress,
            @JsonProperty("address_locality")
            @JsonAlias({"addressLocality", "city"})
            String addressLocality,
            @JsonProperty("address_region")
            @JsonAlias({"addressRegion", "province", "provinceCode"})
            String addressRegion,
            @JsonProperty("postal_code")
            @JsonAlias({"postalCode", "zip"})
            String postalCode,
            @JsonProperty("address_country")
            @JsonAlias({"addressCountry", "country", "countryCode"})
            String addressCountry
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutShippingMethod(
            String id,
            String handle,
            String name,
            String title,
            String code
    ) {

        public String resolvedName() {
            return firstPresent(id, handle, name, title, code);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutFulfillment(
            @JsonProperty("shipping_address")
            @JsonAlias({"shippingAddress", "address"})
            CheckoutAddress shippingAddress,
            @JsonProperty("shipping_method")
            @JsonAlias({"shippingMethod", "method"})
            CheckoutShippingMethod shippingMethod,
            List<CheckoutFulfillmentMethod> methods
    ) {

        public CheckoutFulfillment {
            methods = safeList(methods);
        }

        public CheckoutAddress resolvedShippingAddress() {
            if (shippingAddress != null) {
                return shippingAddress;
            }
            if (methods == null) {
                return null;
            }
            for (CheckoutFulfillmentMethod method : methods) {
                CheckoutAddress address = method == null ? null : method.selectedDestination();
                if (address != null) {
                    return address;
                }
            }
            return null;
        }

        public String resolvedShippingMethod() {
            if (shippingMethod != null) {
                return shippingMethod.resolvedName();
            }
            if (methods == null) {
                return null;
            }
            for (CheckoutFulfillmentMethod method : methods) {
                String selected = method == null ? null : method.selectedOption();
                if (selected != null) {
                    return selected;
                }
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutFulfillmentMethod(
            String id,
            String type,
            @JsonProperty("line_item_ids")
            @JsonAlias("lineItemIds")
            List<String> lineItemIds,
            @JsonProperty("selected_destination_id")
            @JsonAlias("selectedDestinationId")
            String selectedDestinationId,
            List<CheckoutAddress> destinations,
            List<CheckoutFulfillmentGroup> groups
    ) {

        public CheckoutFulfillmentMethod {
            lineItemIds = safeList(lineItemIds);
            destinations = safeList(destinations);
            groups = safeList(groups);
        }

        public CheckoutAddress selectedDestination() {
            if (destinations == null || destinations.isEmpty()) {
                return null;
            }
            if (selectedDestinationId == null) {
                return destinations.getFirst();
            }
            return destinations.stream()
                    .filter(destination -> destination != null && selectedDestinationId.equals(destination.id()))
                    .findFirst()
                    .orElse(destinations.getFirst());
        }

        public String selectedOption() {
            if (groups == null) {
                return null;
            }
            for (CheckoutFulfillmentGroup group : groups) {
                String selected = group == null ? null : group.selectedOption();
                if (selected != null) {
                    return selected;
                }
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutFulfillmentGroup(
            String id,
            @JsonProperty("line_item_ids")
            @JsonAlias("lineItemIds")
            List<String> lineItemIds,
            @JsonProperty("selected_option_id")
            @JsonAlias("selectedOptionId")
            String selectedOptionId,
            List<CheckoutFulfillmentOption> options
    ) {

        public CheckoutFulfillmentGroup {
            lineItemIds = safeList(lineItemIds);
            options = safeList(options);
        }

        public String selectedOption() {
            if (selectedOptionId != null && !selectedOptionId.isBlank()) {
                return selectedOptionId;
            }
            if (options == null) {
                return null;
            }
            return options.stream()
                    .filter(option -> option != null && option.isSelected())
                    .map(CheckoutFulfillmentOption::resolvedId)
                    .findFirst()
                    .orElse(null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutFulfillmentOption(
            String id,
            String handle,
            String title,
            String code,
            Boolean selected
    ) {

        public boolean isSelected() {
            return Boolean.TRUE.equals(selected);
        }

        public String resolvedId() {
            return firstPresent(id, handle, title, code);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutDiscounts(
            List<String> codes,
            List<AppliedDiscount> applied
    ) {
        public CheckoutDiscounts {
            codes = safeList(codes);
            applied = safeList(applied);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CheckoutMessage(
            String code,
            String severity,
            String type,
            @JsonAlias("content")
            String message,
            @JsonAlias("path")
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

        public boolean requiresBuyerAction() {
            return matches(severity, "requires_buyer_input")
                    || matches(severity, "requires_buyer_review");
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

        public boolean isUnrecoverable() {
            return matches(code, "unrecoverable")
                    || matches(code, "payment_declined")
                    || matches(code, "mandate_required")
                    || matches(code, "charge_mismatch")
                    || matches(code, "amount_mismatch");
        }

        public boolean isRecoverable() {
            return !isNotFound() && !isUnrecoverable() && (hasText(code) || hasText(message));
        }

        private boolean matches(String value, String expected) {
            return value != null && value.trim().equalsIgnoreCase(expected);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    private static UcpMoney firstMoney(String fallbackCurrency, CheckoutMoney... values) {
        for (CheckoutMoney value : values) {
            UcpMoney money = value == null ? null : value.toUcpMoney(fallbackCurrency);
            if (money != null) {
                return money;
            }
        }
        return null;
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
