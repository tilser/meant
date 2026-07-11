package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutResultMapperEmbeddedConfigurationTest {
    @Test
    void mapsOnlyTypedEmbeddedServiceBinding() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UcpCheckoutResponse response = mapper.readValue("""
                {"ucp":{"version":"2026-01-23","services":{"dev.ucp.shopping":[
                  {"version":"2026-01-23","transport":"embedded","config":{"delegate":["payment.credential"]}}
                ]}},"checkout_id":"checkout-1","status":"requires_escalation",
                "continue_url":"https://shop.example/checkout/1"}
                """, UcpCheckoutResponse.class);
        Cart cart = Cart.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).endpoint("https://shop.example")
                .remoteCartId("cart-1").remoteCartIdHash("hash").rawCartResponse("{}").totalQuantity(1)
                .active(true).createdAt(Instant.now()).updatedAt(Instant.now()).refreshedAt(Instant.now()).build();

        var result = new CheckoutResultMapper(mapper, new CheckoutExecutionPlanner())
                .from(cart, response, MerchantExecutionPolicy.unavailable());

        assertThat(result.embeddedCheckout()).isNotNull();
        assertThat(result.embeddedCheckout().protocolVersion()).isEqualTo("2026-01-23");
        assertThat(result.embeddedCheckout().merchantAllowedDelegations()).containsExactly("payment.credential");
    }
}
