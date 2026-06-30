package com.meant.api.module.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.order.constant.OrderState;
import org.junit.jupiter.api.Test;

class OrderStateTest {

    @Test
    void transitionsMoveForwardThroughFulfillmentStates() {
        OrderState state = OrderState.transition(OrderState.UNKNOWN, OrderState.PROCESSING);
        state = OrderState.transition(state, OrderState.IN_TRANSIT);
        state = OrderState.transition(state, OrderState.DELIVERED);

        assertThat(state).isEqualTo(OrderState.DELIVERED);
    }

    @Test
    void staleWebhookCannotRegressDeliveredOrder() {
        assertThat(OrderState.transition(OrderState.DELIVERED, OrderState.PROCESSING))
                .isEqualTo(OrderState.DELIVERED);
    }

    @Test
    void terminalRefundWinsOverOtherStates() {
        assertThat(OrderState.transition(OrderState.DELIVERED, OrderState.REFUNDED))
                .isEqualTo(OrderState.REFUNDED);
        assertThat(OrderState.transition(OrderState.REFUNDED, OrderState.DELIVERED))
                .isEqualTo(OrderState.REFUNDED);
    }
}
