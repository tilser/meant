package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutUpdateReconciliationServiceTest {
    private final CheckoutUpdateReconciliationService service = new CheckoutUpdateReconciliationService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appliedObservableStateIsProvenAfterAmbiguousOutcome() throws Exception {
        UpdateCheckoutRequest request = request();
        UcpCheckoutResponse response = response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":2}],
                 "buyer":{"email":"buyer@example.test","first_name":"Ada"},
                 "context":{"address_country":"US"},
                 "discounts":{"codes":["SAVE10"]},
                 "fulfillment":{"methods":[{"destinations":[{"id":"home","first_name":"Ada",
                   "street_address":"1 Main","address_locality":"NYC","address_region":"NY",
                   "postal_code":"10001","address_country":"US"}],
                   "groups":[{"id":"delivery","selected_option_id":"express"}]}]}}}
                """);

        assertThat(service.proves(request, response)).isTrue();
    }

    @Test
    void timeoutBeforeApplyOrUnobservableStateIsNotReportedAsSuccess() throws Exception {
        UpdateCheckoutRequest request = request();

        assertThat(service.proves(request, response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":1}],
                 "buyer":{"email":"old@example.test"},"context":{},"discounts":{"codes":[]}}}
                """))).isFalse();
        assertThat(service.proves(request, response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":2}],
                 "buyer":{"email":"buyer@example.test","first_name":"Ada"}}}
                """))).isFalse();
    }

    private UpdateCheckoutRequest request() {
        return new UpdateCheckoutRequest(
                "checkout-1", List.of(new UpdateCheckoutRequest.LineItem("line-1", "variant-1", 2)),
                Map.of("email", "buyer@example.test", "first_name", "Ada"), null,
                "buyer@example.test", "USD", Map.of("address_country", "US"), List.of("SAVE10"),
                Map.of("methods", List.of(Map.of(
                        "destinations", List.of(Map.of(
                                "id", "home", "first_name", "Ada", "street_address", "1 Main",
                                "address_locality", "NYC", "address_region", "NY", "postal_code", "10001",
                                "address_country", "US")),
                        "groups", List.of(Map.of("id", "delivery", "selected_option_id", "express"))))));
    }

    private UcpCheckoutResponse response(String json) throws Exception {
        return objectMapper.readValue(json, UcpCheckoutResponse.class);
    }
}
