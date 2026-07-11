package com.meant.api.module.checkout.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutToolCallContextTest {
    @Test
    void reconciliationKeepsStableLogicalIdempotencyKeyAndExhaustsRefreshBudget() {
        UUID key = UUID.randomUUID();
        CheckoutToolCallContext retry = new CheckoutToolCallContext(key, true).reconciliation();

        assertThat(retry.idempotencyKey()).isEqualTo(key);
        assertThat(retry.unauthorizedRefreshAllowed()).isFalse();
    }
}
