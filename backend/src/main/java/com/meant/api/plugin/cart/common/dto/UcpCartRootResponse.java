package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UcpCartRootResponse(
        String instructions,
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
        @JsonProperty("line_items")
        @JsonAlias({"lineItems", "lines"})
        List<Line> lineItems,
        List<Total> totals,
        String currency,
        @JsonProperty("checkout_url")
        @JsonAlias("checkoutUrl")
        String checkoutUrl,
        @JsonProperty("continue_url")
        @JsonAlias("continueUrl")
        String continueUrl,
        Discounts discounts,
        List<UcpCartResponse.CartMessage> messages,
        List<UcpCartResponse.CartError> errors,
        Map<String, Object> buyer,
        Map<String, Object> context,
        Map<String, Object> signals,
        CartToolArguments.Fulfillment fulfillment,
        String note,
        @JsonProperty("gift_card_codes")
        @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
        List<UcpCartResponse.AppliedCode> giftCardCodes
) {

    public boolean hasCart() {
        return id != null && !id.isBlank();
    }

    public UcpCartResponse toUcpCartResponse() {
        return new UcpCartResponse(
                instructions,
                new UcpCartResponse.Cart(
                        id,
                        createdAt,
                        updatedAt,
                        expiresAt,
                        safeList(lineItems).stream()
                                .filter(Objects::nonNull)
                                .map(line -> line.toCartLine(currency))
                                .toList(),
                        cost(totals, currency),
                        totalQuantity(lineItems),
                        checkoutUrl,
                        continueUrl,
                        discounts == null ? null : discounts.codes(),
                        discounts == null ? null : discounts.applied(),
                        null,
                        giftCardCodes,
                        null,
                        null,
                        List.of(),
                        buyer,
                        context,
                        signals,
                        fulfillment,
                        discounts == null ? null : new CartToolArguments.Discounts(
                                safeList(discounts.codes()).stream()
                                        .map(UcpCartResponse.AppliedCode::code)
                                        .filter(Objects::nonNull)
                                        .toList()),
                        note
                ),
                messages,
                errors
        );
    }

    private static Integer totalQuantity(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return lines.stream()
                .filter(Objects::nonNull)
                .map(Line::quantity)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
    }

    private static UcpCartResponse.Cost cost(List<Total> totals, String currency) {
        UcpCartResponse.Money total = money(total(totals, "total"), currency);
        UcpCartResponse.Money subtotal = money(total(totals, "subtotal"), currency);
        if (subtotal == null) {
            subtotal = total;
        }
        return total == null && subtotal == null ? null : new UcpCartResponse.Cost(total, subtotal);
    }

    private static Total total(List<Total> totals, String type) {
        return safeList(totals).stream()
                .filter(Objects::nonNull)
                .filter(total -> total.type() != null && total.type().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    private static UcpCartResponse.Money money(Total total, String fallbackCurrency) {
        if (total == null || total.amount() == null) {
            return null;
        }
        return new UcpCartResponse.Money(
                total.amount(),
                firstText(total.currency(), fallbackCurrency)
        );
    }

    private static String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            String id,
            Integer quantity,
            @JsonProperty("item")
            @JsonAlias("merchandise")
            Item item,
            List<Total> totals
    ) {

        UcpCartResponse.Line toCartLine(String currency) {
            return new UcpCartResponse.Line(
                    id,
                    quantity,
                    cost(totals, currency),
                    item == null ? null : new UcpCartResponse.Merchandise(
                            item.id(),
                            item.title(),
                            item.product(),
                            item.productId(),
                            item.selectedOptions(),
                            item.components(),
                            item.sellingPlan()
                    )
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String id,
            String title,
            UcpCartResponse.Product product,
            @JsonProperty("product_id") String productId,
            @JsonProperty("selected_options") List<CartAddItem.SelectedOption> selectedOptions,
            List<CartAddItem.Component> components,
            @JsonProperty("selling_plan") CartAddItem.SellingPlan sellingPlan
    ) {
        public Item {
            selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Total(
            String type,
            Object amount,
            @JsonAlias({"currency_code", "currencyCode"})
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Discounts(
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<UcpCartResponse.AppliedCode> codes,
            @JsonDeserialize(using = CartAppliedCodeListDeserializer.class)
            List<UcpCartResponse.AppliedCode> applied
    ) {
    }
}
