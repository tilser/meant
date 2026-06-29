package com.meant.api.plugin.checkout.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.signing.Jcs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutTotalsReconcilerTest {

    private CheckoutTotalsReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new CheckoutTotalsReconciler(new ObjectMapper(), new Jcs());
    }

    @Test
    void matchingCheckoutPasses() {
        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout());

        assertThat(result.match()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void totalsMismatchRejectsCheckout() {
        Map<String, Object> checkout = checkoutWith("total_amount", money(2099L, "USD"));

        assertThatThrownBy(() -> reconciler.rejectIfMismatch(expected(), checkout))
                .isInstanceOf(UcpCheckoutSafetyException.class)
                .hasMessageContaining("total amount mismatch");
    }

    @Test
    void currencyMismatchRejectsCheckout() {
        Map<String, Object> checkout = checkoutWith("total_amount", money(1999L, "EUR"));

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("currency mismatch");
    }

    @Test
    void lineItemMismatchRejectsCheckout() {
        Map<String, Object> checkout = checkoutWith(
                "line_items",
                List.of(line("line-1", "variant-2", 1, 1499L))
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).anyMatch(violation -> violation.contains("line item"));
    }

    @Test
    void shippingMismatchRejectsCheckout() {
        Map<String, Object> checkout = checkoutWith(
                "shipping_address",
                Map.of("country", "US", "postal_code", "10002")
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("shipping address mismatch");
    }

    @Test
    void spendCeilingUsesLessThanOrEqualRule() {
        CheckoutTotalsReconciler.ExpectedCheckout expected = new CheckoutTotalsReconciler.ExpectedCheckout(
                "co_123",
                "merchant-1",
                1999L,
                "USD",
                List.of(expectedLine()),
                200L,
                500L,
                0L,
                shippingAddress(),
                "standard",
                subscriptionTerms(),
                1998L
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected, checkout());

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("total amount exceeds authorized spend ceiling");
    }

    private CheckoutTotalsReconciler.ExpectedCheckout expected() {
        return new CheckoutTotalsReconciler.ExpectedCheckout(
                "co_123",
                "merchant-1",
                1999L,
                "USD",
                List.of(expectedLine()),
                200L,
                500L,
                0L,
                shippingAddress(),
                "standard",
                subscriptionTerms(),
                1999L
        );
    }

    private CheckoutTotalsReconciler.ExpectedLineItem expectedLine() {
        return new CheckoutTotalsReconciler.ExpectedLineItem(
                "line-1",
                "variant-1",
                1,
                1499L,
                "USD"
        );
    }

    private Map<String, Object> checkout() {
        return Map.of("checkout", baseCheckout());
    }

    private Map<String, Object> checkoutWith(String key, Object value) {
        Map<String, Object> checkout = new java.util.LinkedHashMap<>(baseCheckout());
        checkout.put(key, value);
        return Map.of("checkout", checkout);
    }

    private Map<String, Object> baseCheckout() {
        return Map.of(
                "id", "co_123",
                "merchant_id", "merchant-1",
                "total_amount", money(1999L, "USD"),
                "tax_amount", money(200L, "USD"),
                "discount_amount", money(500L, "USD"),
                "tip_amount", money(0L, "USD"),
                "line_items", List.of(line("line-1", "variant-1", 1, 1499L)),
                "shipping_address", shippingAddress(),
                "shipping_method", Map.of("handle", "standard"),
                "subscription", subscriptionTerms()
        );
    }

    private Map<String, Object> line(String id, String variantId, Integer quantity, Long totalAmount) {
        return Map.of(
                "id", id,
                "product_variant_id", variantId,
                "quantity", quantity,
                "total_amount", money(totalAmount, "USD")
        );
    }

    private Map<String, Object> money(Long amount, String currency) {
        return Map.of("amount_minor", amount, "currency", currency);
    }

    private Map<String, Object> shippingAddress() {
        return Map.of("country", "US", "postal_code", "10001");
    }

    private Map<String, Object> subscriptionTerms() {
        return Map.of("interval", "month", "trial_days", 0);
    }
}
