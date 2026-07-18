package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CheckoutTotalsReconcilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CheckoutTotalsReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new CheckoutTotalsReconciler();
    }

    @Test
    void matchingCheckoutPasses() {
        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout());

        assertThat(result.match()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void totalsMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith("total_amount", money(2099L, "USD"));

        assertThatThrownBy(() -> reconciler.rejectIfMismatch(expected(), checkout))
                .isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("total amount mismatch");
    }

    @Test
    void currencyMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith("total_amount", money(1999L, "EUR"));

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("currency mismatch");
    }

    @Test
    void merchantMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith("merchant_id", "merchant-2");

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("merchant identity mismatch");
    }

    @Test
    void subscriptionTermsMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith(
                "subscription",
                Map.of("interval", "year", "trial_days", 0)
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("subscription terms mismatch");
    }

    @Test
    void lineItemMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith(
                "line_items",
                List.of(line("line-1", "variant-2", 1, 1499L))
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).anyMatch(violation -> violation.contains("line item"));
    }

    @Test
    void shippingMismatchRejectsCheckout() {
        UcpCheckoutResponse checkout = checkoutWith(
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
                expectedShippingAddress(),
                "standard",
                subscriptionTerms(),
                1998L
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected, checkout());

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("total amount exceeds authorized spend ceiling");
    }

    @Test
    void idLessLineItemsReturnMismatchInsteadOfThrowing() {
        UcpCheckoutResponse checkout = checkoutWith(
                "line_items",
                List.of(lineWithNullableIds(null, null, 1, 1499L))
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).anyMatch(violation -> violation.contains("line item"));
    }

    @Test
    void nullCurrencyReturnsMismatchInsteadOfThrowing() {
        UcpCheckoutResponse checkout = checkoutWith("total_amount", Map.of("amount_minor", 1999L));

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("currency mismatch");
    }

    @Test
    void missingQuantityReturnsMismatchInsteadOfThrowing() {
        UcpCheckoutResponse checkout = checkoutWith(
                "line_items",
                List.of(lineWithNullableIds("line-1", "variant-1", null, 1499L))
        );

        CheckoutTotalsReconciler.ReconciliationResult result = reconciler.reconcile(expected(), checkout);

        assertThat(result.match()).isFalse();
        assertThat(result.violations()).contains("line item variant-1 quantity mismatch");
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
                expectedShippingAddress(),
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

    private UcpCheckoutResponse checkout() {
        return checkoutResponse(baseCheckout());
    }

    private UcpCheckoutResponse checkoutWith(String key, Object value) {
        Map<String, Object> checkout = new java.util.LinkedHashMap<>(baseCheckout());
        checkout.put(key, value);
        return checkoutResponse(checkout);
    }

    private UcpCheckoutResponse checkoutResponse(Map<String, Object> checkout) {
        return objectMapper.convertValue(Map.of("checkout", checkout), UcpCheckoutResponse.class);
    }

    private Map<String, Object> baseCheckout() {
        return Map.of(
                "id", "co_123",
                "merchant_id", "merchant-1",
                "total_amount", money(1999L, "USD"),
                "tax_amount", money(200L, "USD"),
                "totals", List.of(
                        total("discount", 500L),
                        total("tip", 0L)
                ),
                "line_items", List.of(line("line-1", "variant-1", 1, 1499L)),
                "shipping_address", shippingAddressPayload(),
                "shipping_method", Map.of("handle", "standard"),
                "subscription", subscriptionTerms()
        );
    }

    private Map<String, Object> total(String type, Long amount) {
        return Map.of("type", type, "amount", money(amount, "USD"));
    }

    private Map<String, Object> line(String id, String variantId, Integer quantity, Long totalAmount) {
        return Map.of(
                "id", id,
                "product_variant_id", variantId,
                "quantity", quantity,
                "total_amount", money(totalAmount, "USD")
        );
    }

    private Map<String, Object> lineWithNullableIds(
            String id,
            String variantId,
            Object quantity,
            Long totalAmount
    ) {
        Map<String, Object> line = new java.util.LinkedHashMap<>();
        line.put("id", id);
        line.put("product_variant_id", variantId);
        line.put("quantity", quantity);
        line.put("total_amount", money(totalAmount, "USD"));
        return line;
    }

    private Map<String, Object> money(Long amount, String currency) {
        return Map.of("amount_minor", amount, "currency", currency);
    }

    private Map<String, Object> shippingAddressPayload() {
        return Map.of("country", "US", "postal_code", "10001");
    }

    private BuyerConsentShippingAddress expectedShippingAddress() {
        return new BuyerConsentShippingAddress(null, null, null, "10001", "US");
    }

    private JsonNode subscriptionTerms() {
        return objectMapper.valueToTree(Map.of("interval", "month", "trial_days", 0));
    }
}
