package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentGroup;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentMethod;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.List;
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
                 "fulfillment":{"methods":[{"selected_destination_id":"home",
                   "destinations":[{"id":"home","first_name":"Ada",
                   "street_address":"1 Main","address_locality":"NYC","address_region":"NY",
                   "postal_code":"10001","address_country":"US"}],
                   "groups":[{"id":"delivery","selected_option_id":"express"}]}]}}}
                """);

        assertThat(service.proves(request, response)).isTrue();
    }

    @Test
    void rootLevelCheckoutContextIsPreservedForReconciliation() throws Exception {
        UpdateCheckoutRequest request = request();
        UcpCheckoutResponse response = response("""
                {"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":2}],
                 "buyer":{"email":"buyer@example.test","first_name":"Ada"},
                 "context":{"address_country":"US"},"discounts":{"codes":["SAVE10"]},
                 "fulfillment":{"methods":[{"selected_destination_id":"home",
                   "destinations":[{"id":"home","first_name":"Ada","street_address":"1 Main",
                     "address_locality":"NYC","address_region":"NY","postal_code":"10001",
                     "address_country":"US"}],
                   "groups":[{"id":"delivery","selected_option_id":"express"}]}]}}
                """);

        assertThat(response.resolvedCheckout().context().addressCountry()).isEqualTo("US");
        assertThat(service.proves(request, response)).isTrue();
    }

    @Test
    void addressPresenceDoesNotProveDifferentSelectedDestination() throws Exception {
        UpdateCheckoutRequest request = request();
        UcpCheckoutResponse response = response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":2}],
                 "buyer":{"email":"buyer@example.test","first_name":"Ada"},
                 "context":{"address_country":"US"},"discounts":{"codes":["SAVE10"]},
                 "fulfillment":{"methods":[{"selected_destination_id":"office",
                   "destinations":[{"id":"home","first_name":"Ada","street_address":"1 Main",
                     "address_locality":"NYC","address_region":"NY","postal_code":"10001",
                     "address_country":"US"}],
                   "groups":[{"id":"delivery","selected_option_id":"express"}]}]}}}
                """);

        assertThat(service.proves(request, response)).isFalse();
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

    @Test
    void explicitDiscountClearIsProvenOnlyWhenRemoteCodesAreEmpty() throws Exception {
        UpdateCheckoutRequest clearRequest = request(List.of());

        assertThat(service.proves(clearRequest, appliedResponse("[]"))).isTrue();
        assertThat(service.proves(clearRequest, appliedResponse("[\"SAVE10\"]"))).isFalse();
    }

    private UpdateCheckoutRequest request() {
        return request(List.of("SAVE10"));
    }

    private UpdateCheckoutRequest request(List<String> discountCodes) {
        return new UpdateCheckoutRequest(
                "checkout-1", List.of(new UpdateCheckoutRequest.LineItem("line-1", "variant-1", 2)),
                new CheckoutBuyer("Ada", null, "buyer@example.test", null), null,
                "buyer@example.test", "USD",
                new CheckoutContext("US", null, null, null, null, null, List.of()),
                discountCodes,
                new CheckoutFulfillment(List.of(new FulfillmentMethod(
                        null,
                        null,
                        List.of(),
                        List.of(new ShippingDestination(
                                "home", null, "1 Main", "NYC", "NY", "US", "10001", "Ada", null, null)),
                        "home",
                        List.of(new FulfillmentGroup("delivery", List.of(), List.of(), "express"))
                )))
        );
    }

    private UcpCheckoutResponse appliedResponse(String discountCodesJson) throws Exception {
        return response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","currency":"USD",
                 "line_items":[{"id":"line-1","product_variant_id":"variant-1","quantity":2}],
                 "buyer":{"email":"buyer@example.test","first_name":"Ada"},
                 "context":{"address_country":"US"},"discounts":{"codes":%s},
                 "fulfillment":{"methods":[{"selected_destination_id":"home",
                   "destinations":[{"id":"home","first_name":"Ada","street_address":"1 Main",
                     "address_locality":"NYC","address_region":"NY","postal_code":"10001",
                     "address_country":"US"}],
                   "groups":[{"id":"delivery","selected_option_id":"express"}]}]}}}
                """.formatted(discountCodesJson));
    }

    private UcpCheckoutResponse response(String json) throws Exception {
        return objectMapper.readValue(json, UcpCheckoutResponse.class);
    }
}
