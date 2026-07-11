package com.meant.api.module.checkout.constant;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutLifecycleStateTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsEveryExplicitAndUnknownLifecycleState() throws Exception {
        Map<String, CheckoutLifecycleState> expected = Map.of(
                "incomplete", CheckoutLifecycleState.INCOMPLETE,
                "requires_escalation", CheckoutLifecycleState.REQUIRES_ESCALATION,
                "ready_for_complete", CheckoutLifecycleState.READY_FOR_COMPLETE,
                "complete_in_progress", CheckoutLifecycleState.PROCESSING,
                "completed", CheckoutLifecycleState.COMPLETED,
                "cancelled", CheckoutLifecycleState.CANCELLED,
                "future_provider_state", CheckoutLifecycleState.UNKNOWN);

        expected.forEach((wire, state) -> assertThat(CheckoutLifecycleState.from(response(
                "{\"checkout\":{\"id\":\"checkout-1\",\"status\":\"" + wire
                        + "\",\"future_extension\":{\"safe\":true}}}"))).isEqualTo(state));
    }

    @Test
    void classifiesRecoverableAndTerminalFailures() throws Exception {
        assertThat(CheckoutLifecycleState.from(response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","messages":[
                {"type":"error","severity":"recoverable","content":"address required"}]}}
                """))).isEqualTo(CheckoutLifecycleState.RECOVERABLE_FAILURE);
        assertThat(CheckoutLifecycleState.from(response("""
                {"checkout":{"id":"checkout-1","status":"incomplete","messages":[
                {"type":"error","severity":"unrecoverable","content":"checkout expired"}]}}
                """))).isEqualTo(CheckoutLifecycleState.TERMINAL_FAILURE);
    }

    private UcpCheckoutResponse response(String json) {
        try {
            return objectMapper.readValue(json, UcpCheckoutResponse.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
