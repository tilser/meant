package com.meant.api.module.checkout.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutToolCallContextTest {
    @Test
    void reconciliationKeepsStableRequestMetadataAndExhaustsRefreshBudget() {
        UUID key = UUID.randomUUID();
        CheckoutToolCallContext retry = new CheckoutToolCallContext(
                key, true, "203.0.113.42").reconciliation();

        assertThat(retry.idempotencyKey()).isEqualTo(key);
        assertThat(retry.unauthorizedRefreshAllowed()).isFalse();
        assertThat(retry.buyerIp()).isEqualTo("203.0.113.42");
    }
}
