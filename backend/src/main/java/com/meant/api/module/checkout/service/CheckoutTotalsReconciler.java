package com.meant.api.module.checkout.service;

import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.JsonNode;

@Service
@Validated
public class CheckoutTotalsReconciler {

    public ReconciliationResult reconcile(
            @NotNull @Valid ExpectedCheckout expected,
            @NotNull UcpCheckoutResponse checkoutResponse
    ) {
        ObservedCheckout observed = observedCheckout(checkoutResponse);
        List<String> violations = new ArrayList<>();

        requireEqual("checkout id", expected.checkoutId(), observed.checkoutId(), violations);
        requireEqual("merchant identity", expected.merchantId(), observed.merchantId(), violations);
        requireEqual(
                "currency",
                normalizedCurrency(expected.currency()),
                normalizedCurrency(observed.currency()),
                violations
        );
        requireEqual("total amount", expected.totalAmountMinor(), observed.totalAmountMinor(), violations);
        requireOptionalEqual("tax amount", expected.taxAmountMinor(), observed.taxAmountMinor(), violations);
        requireOptionalEqual("discount amount", expected.discountAmountMinor(), observed.discountAmountMinor(), violations);
        requireOptionalEqual("tip amount", expected.tipAmountMinor(), observed.tipAmountMinor(), violations);
        requireOptionalEqual("shipping method", expected.shippingMethod(), observed.shippingMethod(), violations);
        requireOptionalEqual("shipping address", expected.shippingAddress(), observed.shippingAddress(), violations);
        requireOptionalEqual("subscription terms", expected.subscriptionTerms(), observed.subscriptionTerms(), violations);
        reconcileLineItems(expected.lineItems(), observed.lineItems(), violations);
        reconcileSpendCeiling(expected, observed, violations);

        return new ReconciliationResult(violations.isEmpty(), List.copyOf(violations), observed);
    }

    public void rejectIfMismatch(
            @NotNull @Valid ExpectedCheckout expected,
            @NotNull UcpCheckoutResponse checkoutResponse
    ) {
        ReconciliationResult result = reconcile(expected, checkoutResponse);
        if (!result.match()) {
            throw new CheckoutSafetyException("Checkout totals reconciliation failed: "
                    + String.join("; ", result.violations()));
        }
    }

    private void reconcileSpendCeiling(
            ExpectedCheckout expected,
            ObservedCheckout observed,
            List<String> violations
    ) {
        if (expected.maxAuthorizedAmountMinor() == null || observed.totalAmountMinor() == null) {
            return;
        }
        if (observed.totalAmountMinor() > expected.maxAuthorizedAmountMinor()) {
            violations.add("total amount exceeds authorized spend ceiling");
        }
    }

    private void reconcileLineItems(
            List<ExpectedLineItem> expectedItems,
            List<ObservedLineItem> observedItems,
            List<String> violations
    ) {
        List<ExpectedLineItem> expected = safeList(expectedItems).stream()
                .sorted(Comparator.comparing(this::lineKey))
                .toList();
        List<ObservedLineItem> observed = safeList(observedItems).stream()
                .sorted(Comparator.comparing(this::lineKey))
                .toList();
        if (expected.size() != observed.size()) {
            violations.add("line item count mismatch");
            return;
        }
        for (int index = 0; index < expected.size(); index++) {
            ExpectedLineItem expectedLine = expected.get(index);
            ObservedLineItem observedLine = observed.get(index);
            String lineContext = "line item " + lineKey(expectedLine);
            requireEqual(lineContext + " key", lineKey(expectedLine), lineKey(observedLine), violations);
            requireEqual(lineContext + " quantity", expectedLine.quantity(), observedLine.quantity(), violations);
            requireEqual(
                    lineContext + " total amount",
                    expectedLine.totalAmountMinor(),
                    observedLine.totalAmountMinor(),
                    violations
            );
            requireEqual(
                    lineContext + " currency",
                    normalizedCurrency(expectedLine.currency()),
                    normalizedCurrency(observedLine.currency()),
                    violations
            );
        }
    }

    private ObservedCheckout observedCheckout(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout == null) {
            return new ObservedCheckout(
                    null, null, null, null, null, null, null, null, null, null, List.of());
        }

        UcpMoney total = checkout.resolvedTotal();
        String currency = checkout.resolvedCurrency(total == null ? null : total.currency());
        return new ObservedCheckout(
                checkout.id(),
                response.resolvedMerchantId(),
                total == null ? null : total.amount(),
                currency,
                firstPresent(
                        checkout.resolvedTaxAmountMinor(),
                        totalAmount(checkout.totals(), currency, "tax", "tax_amount", "total_tax_amount")
                ),
                totalAmount(
                        checkout.totals(),
                        currency,
                        "discount", "discount_amount", "total_discount_amount", "discounts"
                ),
                totalAmount(checkout.totals(), currency, "tip", "tip_amount", "gratuity"),
                shippingAddress(checkout.resolvedShippingAddress()),
                checkout.resolvedShippingMethod(),
                response.resolvedSubscriptionTerms(),
                observedLineItems(checkout.lineItems(), currency)
        );
    }

    private List<ObservedLineItem> observedLineItems(
            List<UcpCheckoutResponse.CheckoutLineItem> lineItems,
            String fallbackCurrency
    ) {
        return safeList(lineItems).stream()
                .filter(Objects::nonNull)
                .map(line -> observedLineItem(line, fallbackCurrency))
                .toList();
    }

    private ObservedLineItem observedLineItem(
            UcpCheckoutResponse.CheckoutLineItem line,
            String fallbackCurrency
    ) {
        UcpMoney total = line.resolvedTotal(fallbackCurrency);
        return new ObservedLineItem(
                line.resolvedId(),
                line.resolvedVariantId(),
                line.quantity(),
                total == null ? null : total.amount(),
                total == null ? fallbackCurrency : firstPresent(total.currency(), fallbackCurrency)
        );
    }

    private Long totalAmount(
            List<UcpCheckoutResponse.CheckoutTotal> totals,
            String fallbackCurrency,
            String... expectedTypes
    ) {
        for (UcpCheckoutResponse.CheckoutTotal total : safeList(totals)) {
            if (total == null || !matchesTotalType(total, expectedTypes)) {
                continue;
            }
            UcpMoney money = total.resolvedMoney(fallbackCurrency);
            if (money != null) {
                return money.amount();
            }
        }
        return null;
    }

    private boolean matchesTotalType(
            UcpCheckoutResponse.CheckoutTotal total,
            String... expectedTypes
    ) {
        String type = normalizedType(firstPresent(total.type(), total.code(), total.name()));
        if (type == null) {
            return false;
        }
        for (String expectedType : expectedTypes) {
            if (type.equals(normalizedType(expectedType))) {
                return true;
            }
        }
        return false;
    }

    private BuyerConsentShippingAddress shippingAddress(UcpCheckoutResponse.CheckoutAddress address) {
        if (address == null) {
            return null;
        }
        BuyerConsentShippingAddress normalized = new BuyerConsentShippingAddress(
                address.streetAddress(),
                address.addressLocality(),
                address.addressRegion(),
                address.postalCode(),
                address.addressCountry()
        );
        return normalized.isEmpty() ? null : normalized;
    }

    private <T> void requireEqual(String field, T expected, T actual, List<String> violations) {
        if (!Objects.equals(expected, actual)) {
            violations.add(field + " mismatch");
        }
    }

    private <T> void requireOptionalEqual(String field, T expected, T actual, List<String> violations) {
        if (expected != null && !Objects.equals(expected, actual)) {
            violations.add(field + " mismatch");
        }
    }

    private String normalizedCurrency(String currency) {
        String normalized = UcpDecimal.normalizedCurrency(currency);
        return normalized == null ? "" : normalized;
    }

    private String normalizedType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private <T> T firstPresent(T first, T second) {
        return first == null ? second : first;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String lineKey(ExpectedLineItem line) {
        return Objects.requireNonNullElse(firstPresent(line.productVariantId(), line.id()), "");
    }

    private String lineKey(ObservedLineItem line) {
        return Objects.requireNonNullElse(firstPresent(line.productVariantId(), line.id()), "");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ExpectedCheckout(
            @NotBlank String checkoutId,
            @NotBlank String merchantId,
            @NotNull Long totalAmountMinor,
            @NotBlank String currency,
            List<@Valid ExpectedLineItem> lineItems,
            Long taxAmountMinor,
            Long discountAmountMinor,
            Long tipAmountMinor,
            @Valid BuyerConsentShippingAddress shippingAddress,
            String shippingMethod,
            JsonNode subscriptionTerms,
            Long maxAuthorizedAmountMinor
    ) {
    }

    public record ExpectedLineItem(
            String id,
            String productVariantId,
            @NotNull Integer quantity,
            @NotNull Long totalAmountMinor,
            @NotBlank String currency
    ) {
    }

    public record ObservedCheckout(
            String checkoutId,
            String merchantId,
            Long totalAmountMinor,
            String currency,
            Long taxAmountMinor,
            Long discountAmountMinor,
            Long tipAmountMinor,
            BuyerConsentShippingAddress shippingAddress,
            String shippingMethod,
            JsonNode subscriptionTerms,
            List<ObservedLineItem> lineItems
    ) {
    }

    public record ObservedLineItem(
            String id,
            String productVariantId,
            Integer quantity,
            Long totalAmountMinor,
            String currency
    ) {
    }

    public record ReconciliationResult(
            boolean match,
            List<String> violations,
            ObservedCheckout observedCheckout
    ) {
    }
}
