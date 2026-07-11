package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutCancellationPolicyTest {
    private final CheckoutCancellationPolicy policy = new CheckoutCancellationPolicy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyExplicitUnblockedCancellation() throws Exception {
        assertThatCode(() -> policy.requireCancelled(response(
                "{\"checkout\":{\"id\":\"checkout-1\",\"status\":\"canceled\"}}")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsStillActiveDirectRejectionAndMalformedBusinessState() throws Exception {
        assertRejected("{\"checkout\":{\"id\":\"checkout-1\",\"status\":\"incomplete\"}}");
        assertRejected("{\"checkout\":{\"id\":\"checkout-1\",\"status\":\"canceled\"},"
                + "\"errors\":[{\"code\":\"cannot_cancel\",\"message\":\"active payment\"}]}");
        assertRejected("{\"checkout\":{\"id\":\"checkout-1\"}}");
    }

    private void assertRejected(String json) throws Exception {
        assertThatThrownBy(() -> policy.requireCancelled(response(json)))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("did not cancel");
    }

    private UcpCheckoutResponse response(String json) throws Exception {
        return objectMapper.readValue(json, UcpCheckoutResponse.class);
    }
}
